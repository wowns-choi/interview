package com.example.interview.web.dto;

import java.util.List;

import com.example.interview.domain.InterviewQuestion;

import lombok.Value;

/** 질문 + 파싱된 꼬리질문 + 근거 평가항목 이름 */
@Value
public class QuestionView {
    InterviewQuestion question;
    List<String> followUps;
    String criterionName;
    /** 맞춤으로 고친 공통 질문의 원문. 그 밖에는 null */
    String originalContent;
}
