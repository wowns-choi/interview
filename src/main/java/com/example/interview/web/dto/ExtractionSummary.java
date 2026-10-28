package com.example.interview.web.dto;

import java.time.LocalDateTime;

import com.example.interview.domain.CandidateStatus;

import lombok.Data;

/** 추출 이력 목록 한 줄 */
@Data
public class ExtractionSummary {
    private Long candidateId;
    private String displayName;
    private String positionTitle;
    private CandidateStatus status;
    private LocalDateTime createdAt;
    private boolean interviewed;
    private LocalDateTime interviewDate;
    private String decision;
}
