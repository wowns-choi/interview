package com.example.interview.domain;

import lombok.Data;

/** 면접관이 직접 만든 질문 유형. 이름만 있고, 질문을 만들 때 AI에게 그 이름으로 넘긴다. */
@Data
public class CustomQuestionType {
    private Long id;
    private String name;
}
