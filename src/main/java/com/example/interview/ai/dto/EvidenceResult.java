package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.interview.ai.AiResponseInvalidException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** 호출 ② 근거 분석 응답. 평가항목별 근거와, 같은 이력서 글로 계산한 총 경력·관련 경력 */
@Data
public class EvidenceResult {

    private List<CriterionResult> results;

    private Career career;

    /** 이력서의 자격증과 정보처리기사·SQLD 보유 여부 */
    private CareerEvalResult.Certificates certificates;

    /** 이름표(A사, 학교1, 교육1, 활동1)마다 이력서에 적힌 기간 (사용자 2026-11-10: 타임라인 호출을 따로 하지 않는다) */
    private List<CareerEvalResult.Period> timeline;

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

    /** 회사 이름은 "A사"처럼 바꿔 보낸다 */
    @Data
    public static class Career {
        @JsonProperty("per_company")
        private List<CareerEvalResult.Company> perCompany;
        @JsonProperty("total_months")
        private Integer totalMonths;
        @JsonProperty("total_reason")
        private String totalReason;
        @JsonProperty("relevant_months")
        private Integer relevantMonths;
        @JsonProperty("relevant_reason")
        private String relevantReason;
        /** 같은 응답의 certificates를 경력 계산과 함께 저장하려고 옮겨 둔다 (응답 JSON의 career에는 없다) */
        @JsonIgnore
        private CareerEvalResult.Certificates certificates;
        /** 같은 응답의 timeline (읽지 못한 항목은 뺀 것). 응답 JSON의 career에는 없다 */
        @JsonIgnore
        private List<CareerEvalResult.Period> timeline;
    }

    /** 입력한 모든 criterion_id에 결과가 있어야 하고, 경력 계산이 있어야 한다. */
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
        validateCareer(errors);
        validateCertificates(errors);
        if (!errors.isEmpty()) {
            throw new AiResponseInvalidException(String.join("; ", errors));
        }
        career.totalReason = career.totalReason == null ? "" : career.totalReason;
        career.relevantReason = career.relevantReason == null ? "" : career.relevantReason;
        if (certificates.getItems() == null) {
            certificates.setItems(List.of());
        }
        career.certificates = certificates;
        // 타임라인은 잘못된 항목만 뺀다. 비어도 근거 분석을 실패로 보지 않는다
        timeline = TimelineResult.clean(timeline);
        career.timeline = timeline;
    }

    private void validateCertificates(List<String> errors) {
        if (certificates == null) {
            errors.add("certificates 누락");
        } else if (certificates.getInfoEngineer() == null || certificates.getSqld() == null) {
            errors.add("certificates.info_engineer와 certificates.sqld는 true 또는 false여야 함");
        }
    }

    private void validateCareer(List<String> errors) {
        if (career == null) {
            errors.add("career 누락");
            return;
        }
        if (career.perCompany == null) {
            errors.add("career.per_company 누락 (경력이 없으면 빈 배열)");
        }
        if (career.totalMonths == null || career.totalMonths < 0) {
            errors.add("career.total_months는 0 이상의 정수여야 함");
        }
        if (career.relevantMonths == null || career.relevantMonths < 0) {
            errors.add("career.relevant_months는 0 이상의 정수여야 함");
        } else if (career.totalMonths != null && career.relevantMonths > career.totalMonths) {
            errors.add("career.relevant_months가 total_months보다 큼");
        }
    }
}
