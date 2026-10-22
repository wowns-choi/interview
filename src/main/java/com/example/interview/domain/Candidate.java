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
    private CandidateStatus status = CandidateStatus.UPLOADED;
    private String errorMessage;
    private LocalDateTime createdAt;
}
