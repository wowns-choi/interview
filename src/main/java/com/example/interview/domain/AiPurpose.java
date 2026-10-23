package com.example.interview.domain;

public enum AiPurpose {
    PII_DETECT, CRITERIA_EXTRACT, EVIDENCE, EDUCATION_EVAL, TIMELINE, QUESTION, ARRANGE,
    /** AI 의견 참고: 뽑는다면·안 뽑는다면 그 이유 (결정은 하지 않는다) */
    OPINION,
    /** 예전 자동 답변 채우기. ai_call_log의 예전 행만 쓴다 */
    AUTO_ANSWER,
    /** 면접 녹음본으로 질문-답변 목록 만들기 */
    INTERVIEW_RECORD,
    /** 보고서: 파견 가능 여부·입사 가능 시기·희망연봉 찾기 */
    REPORT_INFO,
    /** 보고서 질문 정렬 (사용자가 2026-10-23 뺐다. 예전 로그 행에만 남는다) */
    REPORT_SORT,
    /** 예전 학력·타임라인 한 번 호출. ai_call_log의 예전 행만 쓴다 */
    CAREER_EVAL
}
