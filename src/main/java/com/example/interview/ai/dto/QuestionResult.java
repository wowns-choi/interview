package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.List;

import com.example.interview.ai.AiResponseInvalidException;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** 호출 ③-1 질문 추출 응답: 평가항목을 검증하는 질문들 (섹션·순서 없음) */
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

    public void validate() {
        if (questions == null || questions.isEmpty()) {
            throw new AiResponseInvalidException("questions 배열이 비어 있습니다.");
        }
        List<String> errors = new ArrayList<>();
        if (questions.size() > MAX_QUESTIONS) {
            errors.add("질문은 최대 " + MAX_QUESTIONS + "개 (현재 " + questions.size() + "개)");
        }
        for (int i = 0; i < questions.size(); i++) {
            Item q = questions.get(i);
            if (q == null || q.content == null || q.content.isBlank()) {
                errors.add("questions[" + i + "].content 누락");
                continue;
            }
            if (!List.of("VERIFY", "DEPTH", "GAP").contains(q.questionType)) {
                errors.add("questions[" + i + "].question_type은 VERIFY, DEPTH, GAP 중 하나여야 함");
            }
            if (q.priority == null || q.priority < 1 || q.priority > 3) {
                errors.add("questions[" + i + "].priority는 1~3");
            }
        }
        if (!errors.isEmpty()) {
            throw new AiResponseInvalidException(String.join("; ", errors));
        }
    }
}
