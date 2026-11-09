package com.example.interview.domain;

public enum AiPurpose {
    PII_DETECT, CRITERIA_EXTRACT, EVIDENCE, EDUCATION_EVAL, TIMELINE, QUESTION, ARRANGE,
    /** 예전 AI 의견 참고 (뽑는다면·안 뽑는다면). 2026-10-28에 AI와 대화로 바뀌었다. 예전 로그 행에만 남는다 */
    OPINION,
    /** AI와 대화: 면접 전·면접 후 자료를 주고 면접관이 자유롭게 묻는다 */
    CHAT,
    /** 예전 자동 답변 채우기. ai_call_log의 예전 행만 쓴다 */
    AUTO_ANSWER,
    /** 면접 녹음본으로 질문-답변 목록 만들기 */
    INTERVIEW_RECORD,
    /** 보고서: 파견 가능 여부·입사 가능 시기·희망연봉 찾기 */
    REPORT_INFO,
    /** 보고서 질문 정렬 (사용자가 2026-10-23 뺐다. 예전 로그 행에만 남는다) */
    REPORT_SORT,
    /** 질문지에 넣기: 새 질문을 이미 있는 질문지의 어디에 넣을지 */
    QUESTION_PLACE,
    /** 예전 경력 조건 충족 판단. 2026-11-09부터 코드가 요구 경력 개월과 비교한다. ai_call_log의 예전 행만 쓴다 */
    CAREER_MEETS,
    /** 예전 학력·타임라인 한 번 호출. ai_call_log의 예전 행만 쓴다 */
    CAREER_EVAL
}
