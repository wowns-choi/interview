package com.example.interview.domain;

import lombok.Data;

@Data
public class InterviewQuestion {
    private Long id;
    private Long candidateId;
    /** 질문지를 만들 당시의 섹션 이름 */
    private String section;
    private QuestionOrigin origin;
    private QuestionType questionType;
    private String content;
    private String intent;
    private String goodSignal;
    private String warningSignal;
    /** JSON 배열 문자열 */
    private String followUps;
    private String resumeAnsweredYn = "N";
    private Long templateId;
    private Long criterionId;
    private Long evidenceId;
    private Integer priority;
    private int sortOrder;
    /** 면접 중 기록한 실제 답변 */
    private String answer;
}
