package com.example.interview.ai.dto;

import java.util.List;

import com.example.interview.ai.AiResponseInvalidException;

import lombok.Data;

/** 면접 녹음본으로 만든 질문-답변 (녹음본 순서 그대로) */
@Data
public class InterviewRecordResult {

    private List<Item> items;

    @Data
    public static class Item {
        /** INTERVIEWER: 면접관이 물은 것, CANDIDATE: 지원자가 회사에 물은 것 */
        private String kind;
        /** 보고서에 쓸 수 있게 다듬은 질문 */
        private String question;
        /** 답을 줄인 것 */
        private String answer;
        /** 녹음본에서 글자 그대로 옮긴 답 */
        private String excerpt;
    }

    /** 질문이 빈 항목은 버린다. 하나도 남지 않으면 다시 묻는다 */
    public void validate() {
        if (items == null) {
            throw new AiResponseInvalidException("items 배열이 없습니다.");
        }
        items = items.stream().filter(i -> i != null && i.question != null && !i.question.isBlank()).toList();
        if (items.isEmpty()) {
            throw new AiResponseInvalidException("녹음본에서 질문을 하나도 찾지 못했습니다.");
        }
    }
}
