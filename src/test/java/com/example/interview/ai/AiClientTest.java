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

import com.example.interview.ai.dto.CriteriaResult;
import com.example.interview.config.AiProperties;
import com.example.interview.domain.AiCallLog;
import com.example.interview.domain.AiPurpose;

import tools.jackson.databind.json.JsonMapper;

class AiClientTest {

    private static final String VALID = """
            {"criteria":[{"name":"Java 백엔드 개발 경험","description":"Spring 기반 API 개발","requirement":"MUST","category":"TECH","resume_assessable":true}]}""";

    private MockRestServiceServer server;
    private AiCallLogger callLogger;
    private AiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        callLogger = mock(AiCallLogger.class);
        AiProperties props = new AiProperties("test-key", "http://ai.test/v1", "test-model", 1000,
                "max_completion_tokens", true, 5, null, null);
        client = new AiClient(builder.build(), props, callLogger);
    }

    @Test
    void parsesResponseAndLogsUsage() {
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.max_completion_tokens").value(1000))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
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
        String missingField = "{\"criteria\":[{\"name\":\"x\",\"requirement\":\"MUST\",\"category\":\"TECH\"}]}";
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
        AiProperties noKey = new AiProperties("", "http://ai.test/v1", "m", 10, null, null, null, null, null);
        AiClient c = new AiClient(RestClient.create(), noKey, callLogger);
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
