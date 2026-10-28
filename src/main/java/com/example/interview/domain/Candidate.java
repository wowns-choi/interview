package com.example.interview.domain;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class Candidate {
    private Long id;
    private Long positionId;
    private String displayName;
    private String region;
    private String careerLevel;
    private String maskedText;
    /** 로컬 AI가 옮겨 적은 학력·경력·연봉 (CandidateProfile JSON). 외부 AI에는 보내지 않는다 */
    private String profileJson;
    /** 외부 AI가 계산한 총 경력·관련 경력·최종 학력 (CareerEvalResult JSON). 적합도 검사 전에는 null */
    private String careerEvalJson;
    /** AI 의견 참고 (OpinionResult JSON). 아직 묻지 않았으면 null */
    private String opinionJson;
    /** 면접을 봤는지 (면접관이 체크) */
    private boolean interviewed;
    private LocalDateTime interviewDate;
    /** PASS, FAIL. 정하지 않았으면 null. 면접관이 정한다 (AI는 정하지 않는다) */
    private String decision;
    /** 면접 녹음본을 글로 옮긴 것. 가리지 않고 그대로 저장한다 (사용자 결정) */
    private String transcript;
    /** 녹음본으로 만든 질문-답변 (InterviewRecord JSON) */
    private String recordJson;
    /** 합격자 보고서 (InterviewReport JSON). 원래 기록과 따로 고친다 */
    private String reportJson;
    private CandidateStatus status = CandidateStatus.UPLOADED;
    private String errorMessage;
    private LocalDateTime createdAt;
}
