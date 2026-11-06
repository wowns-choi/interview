package com.example.interview.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
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

    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*(.*?)\\s*```$", Pattern.DOTALL);

    private final RestClient restClient;
    private final AiProperties props;
    private final RestClient localRestClient;
    private final LocalAiProperties localProps;
    private final AiCallLogger callLogger;
    /** 설정 탭에서 바꿀 수 있는 API 키 */
    private final ApiKeyStore apiKeys;
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public AiClient(RestClient aiRestClient, AiProperties props, RestClient localAiRestClient,
            LocalAiProperties localProps, AiCallLogger callLogger) {
        this(aiRestClient, props, localAiRestClient, localProps, callLogger, new ApiKeyStore(props));
    }

    @Autowired
    public AiClient(@Qualifier("aiRestClient") RestClient aiRestClient, AiProperties props,
            @Qualifier("localAiRestClient") RestClient localAiRestClient, LocalAiProperties localProps,
            AiCallLogger callLogger, ApiKeyStore apiKeys) {
        this.apiKeys = apiKeys;
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
        if (!apiKeys.isSet()) {
            throw new AiCallException("AI API 키가 설정되지 않았습니다. 설정 탭에서 GPT API 키를 넣으세요.");
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userContent(userContent, images)));
        return withRetry(purpose, refId, messages, () -> send(purpose, refId, messages, true), type, validator);
    }

    /**
     * 대화: JSON이 아니라 글로, 스트림으로 받는다 (사용자 2026-10-28). 받는 대로 onDelta에 조각을 넘기고 다 받은 글을 돌려준다.
     * turns는 role(user, assistant)과 content. 아무 조각도 받기 전에 실패했을 때만 한 번 다시 보낸다.
     */
    public String chatStream(AiPurpose purpose, Long refId, String systemPrompt, List<Map<String, String>> turns,
            Consumer<String> onDelta) {
        if (!apiKeys.isSet()) {
            throw new AiCallException("AI API 키가 설정되지 않았습니다. 설정 탭에서 GPT API 키를 넣으세요.");
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        turns.forEach(t -> messages.add(Map.of("role", t.get("role"), "content", t.get("content"))));
        boolean[] started = { false };
        Consumer<String> sink = d -> {
            started[0] = true;
            onDelta.accept(d);
        };
        try {
            return sendStream(purpose, refId, messages, sink).strip();
        } catch (AiCallException first) {
            if (started[0]) {
                throw first;
            }
            log.warn("AI 대화 실패, 한 번 더 보낸다 (refId={}): {}", refId, first.getMessage());
            return sendStream(purpose, refId, messages, sink).strip();
        }
    }

    /** stream: true. 응답은 "data: {...}" 줄들이고 "data: [DONE]"으로 끝난다. 마지막 조각에 usage가 온다 */
    private String sendStream(AiPurpose purpose, Long refId, List<Map<String, Object>> messages, Consumer<String> onDelta) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.modelFor(purpose));
        body.put("messages", messages);
        body.put(props.maxTokensParam(), props.maxTokensFor(purpose));
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));

        AiCallLog callLog = new AiCallLog();
        callLog.setPurpose(purpose);
        callLog.setRefId(refId);
        callLog.setModel(props.modelFor(purpose));
        callLog.setSuccessYn("N");
        long start = System.currentTimeMillis();
        StringBuilder text = new StringBuilder();
        String[] finishReason = { "" };
        try {
            restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKeys.get())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(body)
                    .exchange((req, res) -> {
                        if (res.getStatusCode().isError()) {
                            String err = new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                            throw new AiCallException("AI API 오류 HTTP " + res.getStatusCode().value() + ": " + abbreviate(err));
                        }
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(res.getBody(), java.nio.charset.StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String data = line.substring(5).strip();
                                if (data.equals("[DONE]")) {
                                    break;
                                }
                                JsonNode chunk = objectMapper.readTree(data);
                                JsonNode usage = chunk.path("usage");
                                if (usage.isObject()) {
                                    callLog.setInputTokens(usage.path("prompt_tokens").asInt());
                                    callLog.setOutputTokens(usage.path("completion_tokens").asInt());
                                }
                                JsonNode choice = chunk.path("choices").path(0);
                                String delta = choice.path("delta").path("content").asString(null);
                                if (delta != null && !delta.isEmpty()) {
                                    text.append(delta);
                                    onDelta.accept(delta);
                                }
                                String fr = choice.path("finish_reason").asString(null);
                                if (fr != null && !fr.isEmpty()) {
                                    finishReason[0] = fr;
                                }
                            }
                        }
                        return null;
                    });
            callLog.setLatencyMs(elapsed(start));
            log.info("[시간] GPT {} refId={} model={} {}ms 토큰 입력={} 출력={} finish={}",
                    purpose, refId, callLog.getModel(), callLog.getLatencyMs(), callLog.getInputTokens(),
                    callLog.getOutputTokens(), finishReason[0]);
            if (text.isEmpty()) {
                throw new AiCallException("AI 응답에 텍스트가 없습니다. (finish_reason=" + finishReason[0] + ")");
            }
            callLog.setSuccessYn("Y");
            if ("length".equals(finishReason[0])) {
                callLog.setErrorMessage("finish_reason=length (출력 토큰 한도 도달)");
            }
            return text.toString();
        } catch (AiCallException e) {
            callLog.setLatencyMs(elapsed(start));
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

    private String send(AiPurpose purpose, Long refId, List<Map<String, Object>> messages, boolean json) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.modelFor(purpose));
        body.put("messages", messages);
        body.put(props.maxTokensParam(), props.maxTokensFor(purpose));
        if (json && props.jsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        String effort = props.reasoningEffortFor(purpose);
        if (effort != null) {
            body.put("reasoning_effort", effort);
        }

        AiCallLog callLog = new AiCallLog();
        callLog.setPurpose(purpose);
        callLog.setRefId(refId);
        callLog.setModel(props.modelFor(purpose));
        callLog.setSuccessYn("N");
        long start = System.currentTimeMillis();
        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKeys.get())
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
            log.info("[시간] GPT {} refId={} model={}{} {}ms 토큰 입력={} 출력={} (추론 {}) finish={}",
                    purpose, refId, callLog.getModel(), effort == null ? "" : " effort=" + effort, callLog.getLatencyMs(),
                    callLog.getInputTokens(), callLog.getOutputTokens(),
                    usage.path("completion_tokens_details").path("reasoning_tokens").asInt(), finishReason);
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
        body.put("keep_alive", localProps.keepAlive());
        if (localProps.think() != null) {
            body.put("think", localProps.think());
        }

        AiCallLog callLog = new AiCallLog();
        callLog.setPurpose(purpose);
        callLog.setRefId(refId);
        callLog.setModel(localProps.model());
        callLog.setSuccessYn("N");
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
            // 어디에 시간이 드는지: 모델 올리기(load) · 입력 읽기(prompt) · 출력 쓰기(eval, 생각 포함). Ollama는 ns로 준다
            String thinking = response.path("message").path("thinking").asString("");
            log.info("[시간] 로컬 {} refId={} model={} {}ms (모델 올리기 {}ms, 입력 읽기 {}ms, 출력 {}ms) 토큰 입력={} 출력={} 생각 {}자 답 {}자",
                    purpose, refId, localProps.model(), callLog.getLatencyMs(),
                    response.path("load_duration").asLong() / 1_000_000, response.path("prompt_eval_duration").asLong() / 1_000_000,
                    response.path("eval_duration").asLong() / 1_000_000, inputTokens, outputTokens,
                    thinking.length(), text == null ? 0 : text.length());
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

    /**
     * 로컬 모델을 미리 메모리에 올린다 (프롬프트 없는 /api/generate). 첫 이력서에서 모델 올리는 시간을 빼려고 앱이 켜질 때 부른다.
     * Ollama가 꺼져 있어도 조용히 넘어간다.
     */
    public void warmUpLocal() {
        long start = System.currentTimeMillis();
        try {
            localRestClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", localProps.model(), "keep_alive", localProps.keepAlive()))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[시간] 로컬 모델 미리 올림 model={} keep_alive={} {}ms", localProps.model(), localProps.keepAlive(),
                    elapsed(start));
        } catch (RuntimeException e) {
            log.info("로컬 모델을 미리 올리지 못함 (Ollama가 꺼져 있으면 첫 호출 때 올린다): {}", e.getMessage());
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
