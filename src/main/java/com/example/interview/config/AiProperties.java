package com.example.interview.config;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.example.interview.domain.AiPurpose;

/**
 * AI 호출 설정. 키와 모델 이름은 코드에 두지 않고 설정·환경변수로만 받는다.
 *
 * @param reasoningModel     근거 분석·학력 판단·AI 의견(EVIDENCE, EDUCATION_EVAL, OPINION)에 쓰는 추론 모델. 비우면 model을 쓴다
 *                           (겹치는 재직 기간 계산은 추론 모델만 정확해서 따로 둔다)
 * @param reasoningMaxTokens 추론 모델의 출력 토큰 한도. 추론에 쓰는 토큰도 여기에 포함된다
 * @param maxTokensParam     요청 본문에서 출력 토큰 한도를 담는 필드 이름
 *                           (OpenAI 신형 모델: max_completion_tokens, 그 외 호환 API: max_tokens)
 * @param jsonMode           true면 response_format=json_object를 보낸다
 * @param inputPricePer1m    입력 100만 토큰당 USD (선택, 비용 표시용)
 * @param outputPricePer1m   출력 100만 토큰당 USD (선택, 비용 표시용)
 */
@ConfigurationProperties("ai")
public record AiProperties(
        String apiKey,
        String baseUrl,
        String model,
        String reasoningModel,
        Integer maxTokens,
        Integer reasoningMaxTokens,
        String maxTokensParam,
        Boolean jsonMode,
        Integer timeoutSeconds,
        Double inputPricePer1m,
        Double outputPricePer1m) {

    private static final Set<AiPurpose> REASONING = Set.of(AiPurpose.EVIDENCE, AiPurpose.EDUCATION_EVAL, AiPurpose.OPINION);

    public AiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com/v1";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");
        if (maxTokens == null) {
            maxTokens = 8000;
        }
        if (reasoningMaxTokens == null) {
            reasoningMaxTokens = 25000;
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

    private boolean reasoning(AiPurpose purpose) {
        return REASONING.contains(purpose) && reasoningModel != null && !reasoningModel.isBlank();
    }

    public String modelFor(AiPurpose purpose) {
        return reasoning(purpose) ? reasoningModel : model;
    }

    public int maxTokensFor(AiPurpose purpose) {
        return reasoning(purpose) ? reasoningMaxTokens : maxTokens;
    }

    public boolean hasPricing() {
        return inputPricePer1m != null && outputPricePer1m != null;
    }
}
