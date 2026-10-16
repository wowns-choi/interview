package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.interview.ai.AiResponseInvalidException;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** 호출 ② 근거 분석 응답 */
@Data
public class EvidenceResult {

    private List<CriterionResult> results;

    @Data
    public static class CriterionResult {
        @JsonProperty("criterion_id")
        private Long criterionId;
        private List<Item> evidences;
        @JsonProperty("interview_point")
        private String interviewPoint;
    }

    @Data
    public static class Item {
        private String type;
        private String quote;
        private String interpretation;
    }

    /** 입력한 모든 criterion_id에 결과가 있어야 한다. */
    public void validate(Collection<Long> expectedCriterionIds) {
        if (results == null) {
            throw new AiResponseInvalidException("results 배열이 없습니다.");
        }
        List<String> errors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < results.size(); i++) {
            CriterionResult r = results.get(i);
            if (r == null || r.criterionId == null) {
                errors.add("results[" + i + "].criterion_id 누락");
                continue;
            }
            if (!expectedCriterionIds.contains(r.criterionId)) {
                errors.add("알 수 없는 criterion_id " + r.criterionId);
                continue;
            }
            seen.add(r.criterionId);
            if (r.evidences == null || r.evidences.isEmpty()) {
                errors.add("criterion_id " + r.criterionId + "의 evidences가 비어 있음 (근거가 없으면 type NONE 한 건)");
                continue;
            }
            for (Item e : r.evidences) {
                if (e == null || !List.of("DIRECT", "INDIRECT", "NONE").contains(e.type)) {
                    errors.add("criterion_id " + r.criterionId + "의 evidence type은 DIRECT, INDIRECT, NONE 중 하나여야 함");
                } else if (!"NONE".equals(e.type) && (e.quote == null || e.quote.isBlank())) {
                    errors.add("criterion_id " + r.criterionId + "의 " + e.type + " 근거에 quote 누락");
                }
            }
        }
        for (Long id : expectedCriterionIds) {
            if (!seen.contains(id)) {
                errors.add("criterion_id " + id + "에 대한 결과 누락");
            }
        }
        if (!errors.isEmpty()) {
            throw new AiResponseInvalidException(String.join("; ", errors));
        }
    }
}
