package com.example.interview.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.interview.ai.dto.EducationResult;
import com.example.interview.ai.dto.CriteriaResult;
import com.example.interview.ai.dto.PiiExtract;
import com.example.interview.config.AiProperties;
import com.example.interview.config.LocalAiProperties;
import com.example.interview.domain.AiCallLog;
import com.example.interview.domain.AiPurpose;

import tools.jackson.databind.json.JsonMapper;

class AiClientTest {

    private static final String VALID = """
            {"required_experience":"경력 3년 이상","required_education":"","criteria":[{"name":"Java 백엔드 개발 경험","description":"Spring 기반 API 개발","category":"TECH","resume_assessable":true}]}""";

    private MockRestServiceServer server;
    private MockRestServiceServer localServer;
    private AiCallLogger callLogger;
    private AiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        callLogger = mock(AiCallLogger.class);
        AiProperties props = new AiProperties("test-key", "http://ai.test/v1", "test-model", "test-reasoning-model", 1000, 2000,
                "max_completion_tokens", true, 5, null, null, "low");
        RestClient.Builder localBuilder = RestClient.builder().baseUrl("http://local.test");
        localServer = MockRestServiceServer.bindTo(localBuilder).build();
        client = new AiClient(builder.build(), props, localBuilder.build(),
                new LocalAiProperties("http://local.test", "local-model", 100, 5, true, null), callLogger);
    }

    @Test
    void parsesResponseAndLogsUsage() {
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.max_completion_tokens").value(1000))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.reasoning_effort").doesNotExist())
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("<job_posting>공고</job_posting>"))
                .andRespond(withSuccess(completion(VALID, 120, 45), MediaType.APPLICATION_JSON));

        CriteriaResult result = call();

        assertThat(result.getCriteria()).hasSize(1);
        assertThat(result.getCriteria().get(0).getResumeAssessable()).isTrue();
        AiCallLog log = capturedLogs(1).get(0);
        assertThat(log.getPurpose()).isEqualTo(AiPurpose.CRITERIA_EXTRACT);
        assertThat(log.getRefId()).isEqualTo(42L);
        assertThat(log.getModel()).isEqualTo("test-model");
        assertThat(log.getInputTokens()).isEqualTo(120);
        assertThat(log.getOutputTokens()).isEqualTo(45);
        assertThat(log.getSuccessYn()).isEqualTo("Y");
        assertThat(log.getLatencyMs()).isNotNull();
        server.verify();
    }

    @Test
    void sendsReasoningEffortOnlyForEvidence() {
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("test-reasoning-model"))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andRespond(withSuccess(completion(VALID, 10, 5), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("test-reasoning-model"))
                .andExpect(jsonPath("$.reasoning_effort").doesNotExist())
                .andRespond(withSuccess(completion(VALID, 10, 5), MediaType.APPLICATION_JSON));

        client.call(AiPurpose.EVIDENCE, 1L, "시스템", "본문", CriteriaResult.class, r -> { });
        client.call(AiPurpose.EDUCATION_EVAL, 1L, "시스템", "본문", CriteriaResult.class, r -> { });
        server.verify();
    }

    @Test
    void sendsKeepAliveToLocalModel() {
        localServer.expect(requestTo("http://local.test/api/chat"))
                .andExpect(jsonPath("$.keep_alive").value("1h"))
                .andRespond(withSuccess("""
                        {"message":{"content":"{}"},"prompt_eval_count":10,"eval_count":5}""", MediaType.APPLICATION_JSON));

        client.callLocal(AiPurpose.PII_DETECT, 7L, "시스템", "본문", java.util.Map.of(), PiiExtract.class, r -> { });
        localServer.verify();
    }

    @Test
    void chatStreamPassesDeltasAndLogsUsage() {
        String sse = """
                data: {"choices":[{"delta":{"role":"assistant","content":""}}]}

                data: {"choices":[{"delta":{"content":"뽑는다면 "}}]}

                data: {"choices":[{"delta":{"content":"**배치**"},"finish_reason":null}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                data: {"choices":[],"usage":{"prompt_tokens":300,"completion_tokens":12}}

                data: [DONE]

                """;
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andExpect(jsonPath("$.stream").value(true))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andRespond(withSuccess(sse, MediaType.TEXT_EVENT_STREAM));

        List<String> deltas = new java.util.ArrayList<>();
        String text = client.chatStream(AiPurpose.CHAT, 7L, "시스템", List.of(java.util.Map.of("role", "user", "content", "왜?")),
                deltas::add);

        assertThat(deltas).containsExactly("뽑는다면 ", "**배치**");
        assertThat(text).isEqualTo("뽑는다면 **배치**");
        AiCallLog log = capturedLogs(1).get(0);
        assertThat(log.getSuccessYn()).isEqualTo("Y");
        assertThat(log.getInputTokens()).isEqualTo(300);
        assertThat(log.getOutputTokens()).isEqualTo(12);
        server.verify();
    }

    @Test
    void educationEvalUsesTheReasoningModel() {
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("test-reasoning-model"))
                .andExpect(jsonPath("$.max_completion_tokens").value(2000))
                .andRespond(withSuccess(completion(
                        "{\"final_education\":\"4년제 대학교 졸업\",\"education_meets\":\"MET\"}", 1, 1),
                        MediaType.APPLICATION_JSON));

        EducationResult r = client.call(AiPurpose.EDUCATION_EVAL, 1L, "system", "user", EducationResult.class,
                EducationResult::validate);

        assertThat(r.getFinalEducation()).isEqualTo("4년제 대학교 졸업");
        assertThat(r.getEducationReason()).isEmpty();
        assertThat(capturedLogs(1).get(0).getModel()).isEqualTo("test-reasoning-model");
        server.verify();
    }

    @Test
    void stripsCodeFence() {
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andRespond(withSuccess(completion("```json\n" + VALID + "\n```", 1, 1), MediaType.APPLICATION_JSON));

        assertThat(call().getCriteria()).hasSize(1);
    }

    @Test
    void retriesOnceWithErrorThenSucceeds() {
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"criteria\": [", 10, 5), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[3].content").value(org.hamcrest.Matchers.containsString("JSON 파싱 실패")))
                .andRespond(withSuccess(completion(VALID, 20, 8), MediaType.APPLICATION_JSON));

        assertThat(call().getCriteria()).hasSize(1);
        assertThat(capturedLogs(2)).extracting(AiCallLog::getInputTokens).containsExactly(10, 20);
        server.verify();
    }

    @Test
    void failsAfterSecondInvalidResponse() {
        String missingField = "{\"criteria\":[{\"name\":\"x\",\"category\":\"TECH\"}]}";
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andRespond(withSuccess(completion(missingField, 1, 1), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andRespond(withSuccess(completion(missingField, 1, 1), MediaType.APPLICATION_JSON));

        assertThatThrownBy(this::call)
                .isInstanceOf(AiCallException.class)
                .hasMessageContaining("resume_assessable 누락");
        capturedLogs(2);
        server.verify();
    }

    @Test
    void httpErrorIsLoggedAsFailure() {
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andRespond(withServerError().body("{\"error\":\"boom\"}"));

        assertThatThrownBy(this::call).isInstanceOf(AiCallException.class).hasMessageContaining("HTTP 500");
        AiCallLog log = capturedLogs(1).get(0);
        assertThat(log.getSuccessYn()).isEqualTo("N");
        assertThat(log.getErrorMessage()).contains("boom");
    }

    @Test
    void missingApiKeyFailsWithoutCalling() {
        AiProperties noKey = new AiProperties("", "http://ai.test/v1", "m", null, 10, null, null, null, null, null, null, null);
        AiClient c = new AiClient(RestClient.create(), noKey, RestClient.create(), new LocalAiProperties(null, null, null, null, null, null),
                callLogger);
        assertThatThrownBy(() -> c.call(AiPurpose.EVIDENCE, 1L, "s", "u", CriteriaResult.class, r -> {
        })).isInstanceOf(AiCallException.class).hasMessageContaining("API 키");
    }

    @Test
    void sendsImagesAsContentParts() {
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[1].content[0].type").value("text"))
                .andExpect(jsonPath("$.messages[1].content[0].text").value("<job_posting></job_posting>"))
                .andExpect(jsonPath("$.messages[1].content[1].type").value("image_url"))
                .andExpect(jsonPath("$.messages[1].content[1].image_url.url").value("data:image/png;base64,AQID"))
                .andRespond(withSuccess(completion(VALID, 10, 5), MediaType.APPLICATION_JSON));

        CriteriaResult result = client.call(AiPurpose.CRITERIA_EXTRACT, 1L, "system", "<job_posting></job_posting>",
                List.of(new AiImage("image/png", new byte[] { 1, 2, 3 })), CriteriaResult.class, CriteriaResult::validate);

        assertThat(result.getCriteria()).hasSize(1);
        server.verify();
    }

    @Test
    void callsLocalOllamaWithSchemaAndContextSize() {
        localServer.expect(requestTo("http://local.test/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("local-model"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.format.properties.이름.type").value("array"))
                .andExpect(jsonPath("$.options.temperature").value(0))
                .andExpect(jsonPath("$.options.num_ctx").value(100))
                .andExpect(jsonPath("$.think").value(true))
                .andExpect(jsonPath("$.messages[1].content").value("<resume>홍길동</resume>"))
                .andRespond(withSuccess(ollama("{\"이름\":[\"홍길동\"]}", 30, 10),
                        MediaType.APPLICATION_JSON));

        PiiExtract result = callLocal();

        assertThat(result.getNames()).containsExactly("홍길동");
        assertThat(result.getCareers()).as("빠진 칸은 빈 값").isEmpty();
        AiCallLog log = capturedLogs(1).get(0);
        assertThat(log.getPurpose()).isEqualTo(AiPurpose.PII_DETECT);
        assertThat(log.getModel()).isEqualTo("local-model");
        assertThat(log.getInputTokens()).isEqualTo(30);
        assertThat(log.getSuccessYn()).isEqualTo("Y");
        localServer.verify();
    }

    @Test
    void localCallFailsWhenInputWasTruncated() {
        localServer.expect(requestTo("http://local.test/api/chat"))
                .andRespond(withSuccess(ollama("{}", 95, 5), MediaType.APPLICATION_JSON));

        assertThatThrownBy(this::callLocal).isInstanceOf(AiCallException.class).hasMessageContaining("num_ctx=100");
        assertThat(capturedLogs(1).get(0).getSuccessYn()).isEqualTo("N");
    }

    @Test
    void localCallRetriesOnBrokenJson() {
        localServer.expect(requestTo("http://local.test/api/chat"))
                .andRespond(withSuccess(ollama("{\"이름\": [", 10, 5),
                        MediaType.APPLICATION_JSON));
        localServer.expect(requestTo("http://local.test/api/chat"))
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andRespond(withSuccess(ollama("{}", 10, 5), MediaType.APPLICATION_JSON));

        assertThat(callLocal().getNames()).isEmpty();
        localServer.verify();
    }

    private PiiExtract callLocal() {
        return client.callLocal(AiPurpose.PII_DETECT, 7L, "system", "<resume>홍길동</resume>", PiiExtract.SCHEMA,
                PiiExtract.class, PiiExtract::validate);
    }

    private static String ollama(String content, int promptTokens, int evalTokens) {
        return JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
                "model", "local-model",
                "message", java.util.Map.of("role", "assistant", "content", content),
                "done", true,
                "done_reason", "stop",
                "prompt_eval_count", promptTokens,
                "eval_count", evalTokens));
    }

    private CriteriaResult call() {
        return client.call(AiPurpose.CRITERIA_EXTRACT, 42L, "system", "<job_posting>공고</job_posting>",
                CriteriaResult.class, CriteriaResult::validate);
    }

    private List<AiCallLog> capturedLogs(int expected) {
        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(callLogger, times(expected)).log(captor.capture());
        return captor.getAllValues();
    }

    private static String completion(String content, int promptTokens, int completionTokens) {
        return JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
                "id", "chatcmpl-test",
                "choices", List.of(java.util.Map.of(
                        "index", 0,
                        "message", java.util.Map.of("role", "assistant", "content", content),
                        "finish_reason", "stop")),
                "usage", java.util.Map.of("prompt_tokens", promptTokens, "completion_tokens", completionTokens)));
    }
}
