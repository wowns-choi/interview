package com.example.interview.domain;

import lombok.Data;

@Data
public class Evidence {
    private Long id;
    private Long candidateId;
    private Long criterionId;
    private EvidenceType evidenceType;
    private String quote;
    private String interpretation;
    private String verifiedYn = "N";
    /** 인용이 원문에 없을 때, 원문에서 가장 비슷한 구간 (없으면 null) */
    private String closestQuote;
    private String interviewPoint;
}
