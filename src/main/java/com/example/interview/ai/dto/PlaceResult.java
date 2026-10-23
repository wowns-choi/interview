package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** 질문지에 넣기: 새 질문마다 이미 있는 질문지의 몇 번 질문 뒤에 넣을지 (기존 질문은 건드리지 않는다) */
@Data
public class PlaceResult {

    private List<Placement> placements;

    @Data
    public static class Placement {
        @JsonProperty("question_id")
        private Long questionId;
        /** 질문지 번호(1부터). 이 질문 바로 뒤에 넣는다 */
        private Integer after;
    }

    /**
     * 없는 번호, 중복은 버린다. 빠진 질문은 코드가 같은 평가 기준 질문 옆에 넣으므로 실패하지 않는다.
     *
     * @param sheetSize 보낸 질문지 질문 수
     * @param questionCount 보낸 새 질문 수 (question_id는 1부터)
     */
    public void validate(int sheetSize, int questionCount) {
        List<Placement> kept = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Placement p : placements == null ? List.<Placement>of() : placements) {
            if (p == null || p.questionId == null || p.after == null) {
                continue;
            }
            if (p.questionId >= 1 && p.questionId <= questionCount && p.after >= 1 && p.after <= sheetSize
                    && seen.add(p.questionId)) {
                kept.add(p);
            }
        }
        placements = kept;
    }
}
