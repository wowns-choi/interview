package com.example.interview.web.dto;

import java.util.List;

import lombok.Value;

/** 결과 질문지의 섹션 하나. JSP EL이 getter로 읽으므로 record 대신 값 클래스로 둔다. */
@Value
public class SectionView {
    String section;
    List<QuestionView> questions;
}
