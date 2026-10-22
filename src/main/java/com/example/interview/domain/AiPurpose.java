package com.example.interview.domain;

public enum AiPurpose {
    PII_DETECT, CRITERIA_EXTRACT, EVIDENCE, EDUCATION_EVAL, TIMELINE, QUESTION, ARRANGE,
    /** AI 의견 참고: 뽑는다면·안 뽑는다면 그 이유 (결정은 하지 않는다) */
    OPINION,
    /** 면접 녹취록으로 질문지 답변 채우기 */
    AUTO_ANSWER,
    /** 예전 학력·타임라인 한 번 호출. ai_call_log의 예전 행만 쓴다 */
    CAREER_EVAL
}
