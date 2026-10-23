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
    /** 공통 질문을 지원자에 맞게 고쳤을 때 고치기 전 원문 */
    private String originalContent;
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
    /** 자동 답변 채우기로 넣은 답변의 녹취 원문 부분 (화면에만, PDF에는 없음) */
    private String answerExcerpt;
    /** 면접에서 물어본 질문 (번호를 눌러 체크, 답변을 적으면 자동으로 체크) */
    private boolean checked;
    /** 공통 질문과 합쳐 질문지에 들어갔는지. false면 추출만 해 둔 질문 */
    private boolean merged = true;
    /** 필수 질문 ("필수질문 체크"에서 물었는지 확인). 공통 질문은 설정에서 복사되고, 질문지에서 바꿀 수 있다 */
    private boolean mustAsk;
}
