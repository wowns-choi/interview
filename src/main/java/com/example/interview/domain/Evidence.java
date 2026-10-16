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
    private String interviewPoint;
}
