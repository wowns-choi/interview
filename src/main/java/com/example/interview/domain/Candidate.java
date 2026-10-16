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
    private CandidateStatus status = CandidateStatus.UPLOADED;
    private String errorMessage;
    private LocalDateTime createdAt;
}
