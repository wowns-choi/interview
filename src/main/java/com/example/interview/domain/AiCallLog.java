package com.example.interview.domain;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AiCallLog {
    private Long id;
    private AiPurpose purpose;
    private Long refId;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer latencyMs;
    private String successYn;
    private String errorMessage;
    private LocalDateTime createdAt;
}
