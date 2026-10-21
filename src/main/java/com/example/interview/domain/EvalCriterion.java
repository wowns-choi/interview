package com.example.interview.domain;

import lombok.Data;

@Data
public class EvalCriterion {
    private Long id;
    private Long positionId;
    private String name;
    private String description;
    private CriterionCategory category;
    private String resumeAssessableYn = "Y";
    private CriterionSource source;
    private int sortOrder;
}
