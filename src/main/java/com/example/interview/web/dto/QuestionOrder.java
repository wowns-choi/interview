package com.example.interview.web.dto;

import java.util.List;

/** 질문지 순서 저장 요청의 섹션 하나 */
public record QuestionOrder(String section, List<Long> questionIds) {
}
