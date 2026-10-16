package com.example.interview.web.dto;

import java.util.List;

import com.example.interview.domain.QuestionSection;
import com.example.interview.domain.QuestionTemplate;

import lombok.Value;

/** 공통 질문 설정 화면의 섹션 하나와 그 질문들 */
@Value
public class SectionBoard {
    QuestionSection section;
    List<QuestionTemplate> questions;
}
