package com.example.interview.domain;

public enum AiPurpose {
    PII_DETECT, CRITERIA_EXTRACT, EVIDENCE, EDUCATION_EVAL, TIMELINE, QUESTION, ARRANGE,
    /** 예전 학력·타임라인 한 번 호출. ai_call_log의 예전 행만 쓴다 */
    CAREER_EVAL
}
