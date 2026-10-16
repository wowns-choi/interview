package com.example.interview.domain;

import lombok.Data;

/** 공통 질문 섹션. 이름과 순서는 사용자가 정한다. */
@Data
public class QuestionSection {
    private Long id;
    private String name;
    private int sortOrder;
}
