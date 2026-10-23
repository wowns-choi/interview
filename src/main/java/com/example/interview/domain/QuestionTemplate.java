package com.example.interview.domain;

import lombok.Data;

/** 공통 질문. 모든 지원자 질문지에 들어가고, 관련 근거가 있으면 AI가 이력서에 맞게 다듬는다. */
@Data
public class QuestionTemplate {
    private Long id;
    private Long sectionId;
    private String content;
    private int sortOrder;
    private String activeYn = "Y";
    /** 필수 질문. 질문지에 들어갈 때 함께 복사된다 */
    private boolean mustAsk;
}
