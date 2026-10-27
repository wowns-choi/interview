package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.interview.ai.AiResponseInvalidException;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** 호출 ③-1 질문 추출 응답: 고른 유형의 질문들 (섹션·순서 없음) */
@Data
public class QuestionResult {

    public static final int MAX_QUESTIONS = 15;

    private List<Item> questions;

    @Data
    public static class Item {
        @JsonProperty("question_type")
        private String questionType;
        private String content;
        private String intent;
        @JsonProperty("good_signal")
        private String goodSignal;
        @JsonProperty("warning_signal")
        private String warningSignal;
        @JsonProperty("follow_ups")
        private List<String> followUps;
        @JsonProperty("criterion_id")
        private Long criterionId;
        @JsonProperty("evidence_id")
        private Long evidenceId;
        private Integer priority;
    }

    /**
     * 쓸 수 없는 질문은 버리고 남은 것만 쓴다. 하나도 남지 않을 때만 실패(재시도).
     * 근거 질문은 보낸 직접·간접 근거를 가리켜야 하고 평가항목은 그 근거의 것으로 맞춘다.
     * "해본 적 있어?"는 직접 근거가 없는 평가항목만 가리킨다.
     *
     * @param types              고른 유형 이름
     * @param claimRefs          근거 참조값(1부터) → 그 근거의 평가항목 id. 직접·간접 근거만
     * @param experienceCriteria "해본 적 있어?"로 물을 수 있는 평가항목 id
     * @param claimTypes         근거 질문 유형 이름
     */
    public void validate(Set<String> types, Map<Long, Long> claimRefs, Set<Long> experienceCriteria,
            Set<String> claimTypes) {
        validate(types, claimRefs, experienceCriteria, claimTypes, Set.of(), Set.of());
    }

    /**
     * @param customTypes 면접관이 직접 적은 유형의 코드 (CUSTOM_1…). 근거·평가항목은 있으면 맞는 것만 남기고 없어도 된다
     * @param criteriaIds 이 포지션의 평가항목 id
     */
    public void validate(Set<String> types, Map<Long, Long> claimRefs, Set<Long> experienceCriteria,
            Set<String> claimTypes, Set<String> customTypes, Set<Long> criteriaIds) {
        if (questions == null || questions.isEmpty()) {
            throw new AiResponseInvalidException("questions 배열이 비어 있습니다.");
        }
        List<String> errors = new ArrayList<>();
        List<Item> kept = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Item q = questions.get(i);
            if (q == null || q.content == null || q.content.isBlank()) {
                errors.add("questions[" + i + "].content 누락");
            } else if (!types.contains(q.questionType)) {
                errors.add("questions[" + i + "].question_type은 " + String.join(", ", types) + " 중 하나여야 함");
            } else if (customTypes.contains(q.questionType)) {
                if (q.evidenceId != null && claimRefs.containsKey(q.evidenceId)) {
                    q.criterionId = claimRefs.get(q.evidenceId);
                } else {
                    q.evidenceId = null;
                    if (q.criterionId != null && !criteriaIds.contains(q.criterionId)) {
                        q.criterionId = null;
                    }
                }
                kept.add(q);
            } else if (claimTypes.contains(q.questionType)) {
                if (q.evidenceId == null || !claimRefs.containsKey(q.evidenceId)) {
                    errors.add("questions[" + i + "].evidence_id는 <evidence>의 DIRECT/INDIRECT 근거여야 함");
                } else {
                    q.criterionId = claimRefs.get(q.evidenceId);
                    kept.add(q);
                }
            } else if (q.criterionId == null || !experienceCriteria.contains(q.criterionId)) {
                errors.add("questions[" + i + "].criterion_id는 <experience_criteria>의 평가항목이어야 함");
            } else {
                q.evidenceId = null;
                kept.add(q);
            }
        }
        if (kept.isEmpty()) {
            throw new AiResponseInvalidException(String.join("; ", errors));
        }
        for (Item q : kept) {
            if (q.priority == null || q.priority < 1 || q.priority > 3) {
                q.priority = 2;
            }
        }
        questions = kept.size() > MAX_QUESTIONS ? new ArrayList<>(kept.subList(0, MAX_QUESTIONS)) : kept;
    }
}
