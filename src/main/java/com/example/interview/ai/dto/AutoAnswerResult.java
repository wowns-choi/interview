package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.interview.ai.AiResponseInvalidException;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** 녹취록으로 채운 답변: 질문 번호마다 답변 요약과 녹취 원문 부분. 녹취록에서 답을 찾지 못한 질문은 없다. */
@Data
public class AutoAnswerResult {

    private List<Item> answers;

    @Data
    public static class Item {
        /** 질문지 번호 (1부터) */
        @JsonProperty("question_id")
        private Long questionId;
        /** 지원자가 한 말을 면접관 메모처럼 줄인 것 */
        private String answer;
        /** 녹취록에서 그대로 옮긴 지원자 발화 */
        private String excerpt;
    }

    /** 없는 번호, 중복, 빈 답변은 버린다. 하나도 없어도 된다(녹취록에 답이 없을 수 있다). */
    public void validate(int questionCount) {
        if (answers == null) {
            throw new AiResponseInvalidException("answers 배열이 없습니다.");
        }
        Set<Long> seen = new HashSet<>();
        List<Item> kept = new ArrayList<>();
        for (Item a : answers) {
            if (a != null && a.questionId != null && a.questionId >= 1 && a.questionId <= questionCount
                    && a.answer != null && !a.answer.isBlank() && seen.add(a.questionId)) {
                kept.add(a);
            }
        }
        answers = kept;
    }
}
