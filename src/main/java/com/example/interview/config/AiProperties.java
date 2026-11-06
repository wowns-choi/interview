package com.example.interview.config;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.example.interview.domain.AiPurpose;

/**
 * AI 호출 설정. 키와 모델 이름은 코드에 두지 않고 설정·환경변수로만 받는다.
 *
 * @param reasoningModel     근거 분석·학력·경력 판단·AI 의견(EVIDENCE, EDUCATION_EVAL, CAREER_MEETS, OPINION)에 쓰는 추론 모델. 비우면 model을 쓴다
 *                           (겹치는 재직 기간 계산은 추론 모델만 정확해서 따로 둔다)
 * @param reasoningMaxTokens 추론 모델의 출력 토큰 한도. 추론에 쓰는 토큰도 여기에 포함된다
 * @param maxTokensParam     요청 본문에서 출력 토큰 한도를 담는 필드 이름
 *                           (OpenAI 신형 모델: max_completion_tokens, 그 외 호환 API: max_tokens)
 * @param jsonMode           true면 response_format=json_object를 보낸다
 * @param inputPricePer1m    입력 100만 토큰당 USD (선택, 비용 표시용)
 * @param outputPricePer1m   출력 100만 토큰당 USD (선택, 비용 표시용)
 * @param evidenceReasoningEffort 근거 분석(EVIDENCE)에 보내는 reasoning_effort (minimal, low, medium, high). 비우면 보내지 않는다
 *                           (모델 기본값 medium). 근거 분석이 검사 시간의 3분의 2라 낮춰 본다 (사용자 2026-11-06)
 * @param recordReasoningEffort 면접 내용 정리(INTERVIEW_RECORD)에 보내는 reasoning_effort. 녹음본을 다듬어 옮기는 일이라
 *                           깊은 추론이 필요 없다 (사용자 2026-11-06)
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
        Double outputPricePer1m,
        String evidenceReasoningEffort,
        String recordReasoningEffort) {

    private static final Set<AiPurpose> REASONING = Set.of(AiPurpose.EVIDENCE, AiPurpose.EDUCATION_EVAL, AiPurpose.CAREER_MEETS, AiPurpose.OPINION,
            AiPurpose.INTERVIEW_RECORD);

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

    /** 추론 모델로 가는 근거 분석·면접 내용 정리에만 붙인다. 추론 모델이 아니면 reasoning_effort를 받지 않으므로 보내지 않는다 */
    public String reasoningEffortFor(AiPurpose purpose) {
        String effort = switch (purpose) {
            case EVIDENCE -> evidenceReasoningEffort;
            case INTERVIEW_RECORD -> recordReasoningEffort;
            default -> null;
        };
        if (!reasoning(purpose) || effort == null || effort.isBlank()) {
            return null;
        }
        return effort.strip();
    }

    public boolean hasPricing() {
        return inputPricePer1m != null && outputPricePer1m != null;
    }
}
