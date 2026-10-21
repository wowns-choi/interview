package com.example.interview.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.example.interview.config.AiProperties;
import com.example.interview.config.LocalAiProperties;
import com.example.interview.domain.AiCallLog;
import com.example.interview.domain.AiPurpose;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 모든 AI 호출이 거치는 클라이언트.
 * <ul>
 * <li>{@code call}: 외부 AI (OpenAI 호환 Chat Completions). 개인정보를 가린 글만 보낸다.</li>
 * <li>{@code callLocal}: 로컬 AI (Ollama). 이력서 원문은 이쪽으로만 보낸다.</li>
 * <li>응답 텍스트를 JSON으로 파싱하고 검증한다. 실패하면 오류 내용을 덧붙여 1회만 재요청한다.</li>
 * <li>호출마다 토큰 수와 소요 시간을 ai_call_log에 남긴다.</li>
 * </ul>
 */
@Slf4j
@Component
public class AiClient {

    /** AI에 보낸 메시지와 받은 응답 전문. logging.level.ai.trace로 켜고 끈다 */
    private static final Logger TRACE = LoggerFactory.getLogger("ai.trace");
    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*(.*?)\\s*```$", Pattern.DOTALL);

    private final RestClient restClient;
    private final AiProperties props;
    private final RestClient localRestClient;
    private final LocalAiProperties localProps;
    private final AiCallLogger callLogger;
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public AiClient(@Qualifier("aiRestClient") RestClient aiRestClient, AiProperties props,
            @Qualifier("localAiRestClient") RestClient localAiRestClient, LocalAiProperties localProps,
            AiCallLogger callLogger) {
        this.restClient = aiRestClient;
        this.props = props;
        this.localRestClient = localAiRestClient;
        this.localProps = localProps;
        this.callLogger = callLogger;
    }

    /**
     * @param validator 필수 필드 검증. 문제가 있으면 {@link AiResponseInvalidException}을 던진다.
     */
    public <T> T call(AiPurpose purpose, Long refId, String systemPrompt, String userContent,
            Class<T> type, Consumer<T> validator) {
        return call(purpose, refId, systemPrompt, userContent, List.of(), type, validator);
    }

    /**
     * @param images 사용자 메시지에 함께 붙이는 이미지. 비어 있으면 텍스트만 보낸다.
     */
    public <T> T call(AiPurpose purpose, Long refId, String systemPrompt, String userContent,
            List<AiImage> images, Class<T> type, Consumer<T> validator) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new AiCallException("AI API 키가 설정되지 않았습니다. 환경변수 AI_API_KEY 또는 OPENAI_API_KEY를 설정하세요.");
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userContent(userContent, images)));
        return withRetry(purpose, refId, messages, () -> send(purpose, refId, messages), type, validator);
    }

    /**
     * 로컬 AI(Ollama)를 호출한다. 출력은 schema(JSON Schema)로 강제한다.
     */
    public <T> T callLocal(AiPurpose purpose, Long refId, String systemPrompt, String userContent,
            Map<String, Object> schema, Class<T> type, Consumer<T> validator) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userContent));
        return withRetry(purpose, refId, messages, () -> sendLocal(purpose, refId, messages, schema), type, validator);
    }

    private <T> T withRetry(AiPurpose purpose, Long refId, List<Map<String, Object>> messages,
            Supplier<String> sender, Class<T> type, Consumer<T> validator) {
        String lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String text = sender.get();
            try {
                T result = parse(text, type);
                validator.accept(result);
                return result;
            } catch (AiResponseInvalidException e) {
                lastError = e.getMessage();
                log.warn("AI 응답 검증 실패 (purpose={}, refId={}, attempt={}): {}", purpose, refId, attempt, lastError);
                // 재요청: 직전 응답과 오류 내용을 덧붙인다
                messages.add(Map.of("role", "assistant", "content", text));
                messages.add(Map.of("role", "user", "content",
                        "직전 응답이 형식 검증에 실패했습니다: " + lastError
                                + "\n요구한 JSON 형식을 정확히 지켜, JSON 객체 하나만 다시 출력하세요."));
            }
        }
        throw new AiCallException("AI 응답 형식 오류 (재시도 후에도 실패): " + lastError);
    }

    /** 이미지가 있으면 OpenAI 형식의 content 배열(텍스트 + image_url)로 보낸다. */
    private static Object userContent(String text, List<AiImage> images) {
        if (images == null || images.isEmpty()) {
            return text;
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("type", "text", "text", text));
        for (AiImage image : images) {
            parts.add(Map.of("type", "image_url", "image_url", Map.of("url", image.dataUrl())));
        }
        return parts;
    }

    private String send(AiPurpose purpose, Long refId, List<Map<String, Object>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.modelFor(purpose));
        body.put("messages", messages);
        body.put(props.maxTokensParam(), props.maxTokensFor(purpose));
        if (props.jsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        AiCallLog callLog = new AiCallLog();
        callLog.setPurpose(purpose);
        callLog.setRefId(refId);
        callLog.setModel(props.modelFor(purpose));
        callLog.setSuccessYn("N");
        traceRequest("GPT 요청", purpose, refId, props.modelFor(purpose), messages);
        long start = System.currentTimeMillis();
        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + props.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            callLog.setLatencyMs(elapsed(start));
            if (response == null) {
                throw new AiCallException("AI 응답 본문이 비어 있습니다.");
            }
            JsonNode usage = response.path("usage");
            if (!usage.isMissingNode()) {
                callLog.setInputTokens(usage.path("prompt_tokens").asInt());
                callLog.setOutputTokens(usage.path("completion_tokens").asInt());
            }
            JsonNode choice = response.path("choices").path(0);
            String text = choice.path("message").path("content").asString(null);
            String finishReason = choice.path("finish_reason").asString("");
            TRACE.info("[GPT 응답] purpose={} refId={} model={} {}ms 토큰 입력={} 출력={} finish_reason={}\n{}",
                    purpose, refId, callLog.getModel(), callLog.getLatencyMs(), callLog.getInputTokens(),
                    callLog.getOutputTokens(), finishReason, pretty(text));
            if (text == null || text.isBlank()) {
                throw new AiCallException("AI 응답에 텍스트가 없습니다. (finish_reason=" + finishReason + ")");
            }
            callLog.setSuccessYn("Y");
            if ("length".equals(finishReason)) {
                // 잘린 응답은 파싱 단계에서 실패해 재요청된다
                callLog.setErrorMessage("finish_reason=length (출력 토큰 한도 도달)");
            }
            return text;
        } catch (RestClientResponseException e) {
            callLog.setLatencyMs(elapsed(start));
            callLog.setErrorMessage("HTTP " + e.getStatusCode().value() + ": " + abbreviate(e.getResponseBodyAsString()));
            throw new AiCallException("AI API 오류 " + callLog.getErrorMessage(), e);
        } catch (AiCallException e) {
            callLog.setErrorMessage(e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            callLog.setLatencyMs(elapsed(start));
            callLog.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new AiCallException("AI 호출 실패: " + e.getMessage(), e);
        } finally {
            try {
                callLogger.log(callLog);
            } catch (RuntimeException logError) {
                log.error("ai_call_log 저장 실패", logError);
            }
        }
    }

    /** Ollama 고유 API(/api/chat). OpenAI 호환 API로는 num_ctx를 정할 수 없어 긴 이력서가 잘린다. */
    private String sendLocal(AiPurpose purpose, Long refId, List<Map<String, Object>> messages,
            Map<String, Object> schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", localProps.model());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("format", schema);
        body.put("options", Map.of("temperature", 0, "num_ctx", localProps.numCtx()));
        if (localProps.think() != null) {
            body.put("think", localProps.think());
        }

        AiCallLog callLog = new AiCallLog();
        callLog.setPurpose(purpose);
        callLog.setRefId(refId);
        callLog.setModel(localProps.model());
        callLog.setSuccessYn("N");
        // 이력서 원문이 그대로 들어 있다. 응답 로그와 같이 이 컴퓨터 콘솔에만 남는다
        traceRequest("로컬 AI 요청", purpose, refId, localProps.model(), messages);
        long start = System.currentTimeMillis();
        try {
            JsonNode response = localRestClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            callLog.setLatencyMs(elapsed(start));
            if (response == null) {
                throw new AiCallException("로컬 AI 응답 본문이 비어 있습니다.");
            }
            int inputTokens = response.path("prompt_eval_count").asInt();
            int outputTokens = response.path("eval_count").asInt();
            callLog.setInputTokens(inputTokens);
            callLog.setOutputTokens(outputTokens);
            String text = response.path("message").path("content").asString(null);
            // 이력서 원문의 개인정보가 그대로 들어 있다. 로그는 이 컴퓨터 콘솔에만 남는다
            String thinking = response.path("message").path("thinking").asString(null);
            if (thinking != null && !thinking.isBlank()) {
                TRACE.info("[로컬 AI 생각] purpose={} refId={}\n{}", purpose, refId, thinking.strip());
            }
            TRACE.info("[로컬 AI 응답] purpose={} refId={} model={} {}ms 토큰 입력={} 출력={}\n{}",
                    purpose, refId, localProps.model(), callLog.getLatencyMs(), inputTokens, outputTokens, pretty(text));
            if (inputTokens + outputTokens >= localProps.numCtx()) {
                throw new AiInputTooLongException("로컬 AI 입력 한도(num_ctx=" + localProps.numCtx() + ")를 넘어 입력이 잘렸습니다.");
            }
            if (text == null || text.isBlank()) {
                throw new AiCallException("로컬 AI 응답에 텍스트가 없습니다. (done_reason="
                        + response.path("done_reason").asString("") + ")");
            }
            callLog.setSuccessYn("Y");
            return text;
        } catch (RestClientResponseException e) {
            callLog.setLatencyMs(elapsed(start));
            callLog.setErrorMessage("HTTP " + e.getStatusCode().value() + ": " + abbreviate(e.getResponseBodyAsString()));
            throw new AiCallException("로컬 AI 오류 " + callLog.getErrorMessage(), e);
        } catch (AiCallException e) {
            callLog.setErrorMessage(e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            callLog.setLatencyMs(elapsed(start));
            callLog.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new AiCallException("로컬 AI 호출 실패: " + e.getMessage(), e);
        } finally {
            try {
                callLogger.log(callLog);
            } catch (RuntimeException logError) {
                log.error("ai_call_log 저장 실패", logError);
            }
        }
    }

    <T> T parse(String text, Class<T> type) {
        String json = stripCodeFence(text);
        try {
            T value = objectMapper.readValue(json, type);
            if (value == null) {
                throw new AiResponseInvalidException("JSON이 비어 있습니다.");
            }
            return value;
        } catch (JacksonException e) {
            throw new AiResponseInvalidException("JSON 파싱 실패: " + e.getOriginalMessage(), e);
        }
    }

    static String stripCodeFence(String text) {
        String trimmed = text == null ? "" : text.trim();
        Matcher m = CODE_FENCE.matcher(trimmed);
        return m.matches() ? m.group(1) : trimmed;
    }

    /** 보내는 메시지 전체. 이미지는 개수만 적는다 */
    private void traceRequest(String label, AiPurpose purpose, Long refId, String model, List<Map<String, Object>> messages) {
        if (!TRACE.isInfoEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> m : messages) {
            sb.append("\n----- ").append(m.get("role")).append(" -----\n");
            if (m.get("content") instanceof List<?> parts) {
                long images = parts.stream().filter(p -> p instanceof Map<?, ?> part && "image_url".equals(part.get("type"))).count();
                parts.stream().filter(p -> p instanceof Map<?, ?> part && "text".equals(part.get("type")))
                        .forEach(p -> sb.append(((Map<?, ?>) p).get("text")));
                sb.append("\n(이미지 ").append(images).append("개)");
            } else {
                sb.append(m.get("content"));
            }
        }
        TRACE.info("[{}] purpose={} refId={} model={}{}", label, purpose, refId, model, sb);
    }

    /** JSON이면 들여쓰기해서 읽기 쉽게, 아니면 그대로 */
    private String pretty(String text) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(stripCodeFence(text)));
        } catch (RuntimeException e) {
            return text;
        }
    }

    private static int elapsed(long start) {
        return (int) (System.currentTimeMillis() - start);
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
