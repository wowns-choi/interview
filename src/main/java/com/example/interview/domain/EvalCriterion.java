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
    /** Y면 지운 기준 (실제로는 지우지 않는다: 그 기준으로 검사한 지원자의 근거가 남도록) */
    private String deletedYn = "N";
}
