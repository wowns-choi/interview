package com.example.interview.domain;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * 합격자 보고서. 만들 때 면접 기록·질문지·이력서 정보를 복사해 오고, 그 뒤로는 보고서만 고친다 (원래 기록은 그대로).
 * 지원자 정보·질문과 답변·면접관 의견은 순서대로 저장한다 (화면에서 추가·삭제·순서 바꾸기).
 */
@Data
public class InterviewReport {

    /** 질문과 답변을 면접 녹음본에서 가져왔는지 */
    public static final String RECORD = "RECORD";
    /** 질문과 답변을 면접 질문지(면접관이 적은 답변)에서 가져왔는지 */
    public static final String SHEET = "SHEET";

    /** yyyy-MM-dd */
    private String reportDate = "";
    private String interviewDate = "";
    /** 지원 포지션. null이면 포지션 이름을 쓴다 (예전 보고서). 고쳐도 포지션은 그대로 */
    private String position;
    private String qaSource = RECORD;
    private List<InfoRow> info = new ArrayList<>();
    private List<QaItem> qa = new ArrayList<>();
    private List<Opinion> opinions = new ArrayList<>();

    @Data
    public static class InfoRow {
        private String label = "";
        private String value = "";

        public static InfoRow of(String label, String value) {
            InfoRow r = new InfoRow();
            r.setLabel(label);
            r.setValue(value == null ? "" : value);
            return r;
        }
    }

    @Data
    public static class Opinion {
        private String team = "";
        private String name = "";
        private String content = "";
    }

    @JsonIgnore
    public List<QaItem> getInterviewerQa() {
        return qa.stream().filter(q -> !q.isFromCandidate()).toList();
    }

    @JsonIgnore
    public List<QaItem> getCandidateQa() {
        return qa.stream().filter(QaItem::isFromCandidate).toList();
    }

    /** null 목록·값을 빈 것으로 바꾼다 (화면에서 받은 것을 저장하기 전에) */
    public void clean() {
        info = info == null ? new ArrayList<>() : new ArrayList<>(info.stream().filter(r -> r != null).toList());
        info.forEach(r -> {
            r.setLabel(r.getLabel() == null ? "" : r.getLabel().strip());
            r.setValue(r.getValue() == null ? "" : r.getValue().strip());
        });
        qa = qa == null ? new ArrayList<>() : new ArrayList<>(qa.stream().filter(q -> q != null).toList());
        opinions = opinions == null ? new ArrayList<>() : new ArrayList<>(opinions.stream().filter(o -> o != null).toList());
        opinions.forEach(o -> {
            o.setTeam(o.getTeam() == null ? "" : o.getTeam().strip());
            o.setName(o.getName() == null ? "" : o.getName().strip());
            o.setContent(o.getContent() == null ? "" : o.getContent().strip());
        });
        reportDate = reportDate == null ? "" : reportDate.strip();
        interviewDate = interviewDate == null ? "" : interviewDate.strip();
        position = position == null ? null : position.strip();
        qaSource = SHEET.equals(qaSource) ? SHEET : RECORD;
    }
}
