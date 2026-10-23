package com.example.interview.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/** 질문 하나와 그 답. 면접 기록과 보고서가 같이 쓴다 */
@Data
public class QaItem {

    /** 면접관이 물은 질문 */
    public static final String INTERVIEWER = "INTERVIEWER";
    /** 지원자가 회사에 물은 질문 */
    public static final String CANDIDATE = "CANDIDATE";

    private String kind = INTERVIEWER;
    private String question = "";
    private String answer = "";
    /** 녹음본에서 글자 그대로 옮긴 답변 부분. 보고서 PDF에는 넣지 않는다 */
    private String excerpt = "";

    public static QaItem of(String kind, String question, String answer, String excerpt) {
        QaItem q = new QaItem();
        q.setKind(kind);
        q.setQuestion(question);
        q.setAnswer(answer);
        q.setExcerpt(excerpt);
        return q;
    }

    public void setKind(String kind) {
        this.kind = CANDIDATE.equalsIgnoreCase(kind == null ? "" : kind.strip()) ? CANDIDATE : INTERVIEWER;
    }

    public void setQuestion(String question) {
        this.question = question == null ? "" : question.strip();
    }

    public void setAnswer(String answer) {
        this.answer = answer == null ? "" : answer.strip();
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt == null ? "" : excerpt.strip();
    }

    @JsonIgnore
    public boolean isFromCandidate() {
        return CANDIDATE.equals(kind);
    }
}
