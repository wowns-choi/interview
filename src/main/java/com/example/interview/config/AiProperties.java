package com.example.interview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 호출 설정. 키와 모델 이름은 코드에 두지 않고 설정·환경변수로만 받는다.
 *
 * @param maxTokensParam   요청 본문에서 출력 토큰 한도를 담는 필드 이름
 *                         (OpenAI 신형 모델: max_completion_tokens, 그 외 호환 API: max_tokens)
 * @param jsonMode         true면 response_format=json_object를 보낸다
 * @param inputPricePer1m  입력 100만 토큰당 USD (선택, 비용 표시용)
 * @param outputPricePer1m 출력 100만 토큰당 USD (선택, 비용 표시용)
 */
@ConfigurationProperties("ai")
public record AiProperties(
        String apiKey,
        String baseUrl,
        String model,
        Integer maxTokens,
        String maxTokensParam,
        Boolean jsonMode,
        Integer timeoutSeconds,
        Double inputPricePer1m,
        Double outputPricePer1m) {

    public AiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com/v1";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");
        if (maxTokens == null) {
            maxTokens = 8000;
        }
        if (maxTokensParam == null || maxTokensParam.isBlank()) {
            maxTokensParam = "max_completion_tokens";
        }
        if (jsonMode == null) {
            jsonMode = true;
        }
        if (timeoutSeconds == null) {
            timeoutSeconds = 120;
        }
    }

    public boolean hasPricing() {
        return inputPricePer1m != null && outputPricePer1m != null;
    }
}
