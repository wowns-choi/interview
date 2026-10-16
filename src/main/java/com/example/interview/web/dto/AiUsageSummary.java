package com.example.interview.web.dto;

import lombok.Data;

@Data
public class AiUsageSummary {
    private long callCount;
    private long inputTokens;
    private long outputTokens;
    private long latencyMs;
    private long failedCount;

    /** 화면 표시용 예상 비용(USD). 단가가 설정되지 않았으면 null */
    private Double estimatedCostUsd;

    public long getTotalTokens() {
        return inputTokens + outputTokens;
    }
}
