package com.example.interview.web.dto;

import java.time.LocalDateTime;

import com.example.interview.domain.PositionStatus;

import lombok.Data;

/** 평가 기준 탭의 최근 포지션 목록 한 줄 */
@Data
public class PositionSummary {
    private Long id;
    private String title;
    private PositionStatus status;
    private int criteriaCount;
    private int candidateCount;
    private LocalDateTime createdAt;
}
