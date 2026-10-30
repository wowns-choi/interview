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
    /** 자격증 (지원자 정보와 따로, 사용자 2026-10-28) */
    private List<InfoRow> certs = new ArrayList<>();
    private List<QaItem> qa = new ArrayList<>();
    /**
     * 보이지 않는 쪽(qaSource가 아닌 출처)의 질문과 답변 사본. 보고서를 만들거나 동기화할 때 두 출처를 모두 복사해 두고,
     * 타이핑·녹음본 전환은 이 사본과 qa를 맞바꾸기만 한다 (고친 것도 그대로, 사용자 2026-10-28).
     * null이면 이 칸이 생기기 전에 만든 보고서라, 처음 바꿀 때 그때의 기록으로 채운다.
     */
    private List<QaItem> qaOther;
    private List<Opinion> opinions = new ArrayList<>();
    /** 파견 가능 여부·입사 가능 시기·희망연봉을 찾을 때 보낸 내용의 해시. 같으면 다시 묻지 않고 found를 쓴다 */
    private String infoKey;
    private Found found;

    /** GPT가 면접 내용에서 찾은 값 (보고서에서 고쳐도 그대로) */
    @Data
    public static class Found {
        private String dispatch = "";
        private String startDate = "";
        private String lastSalary = "";
        private String desiredSalary = "";
    }

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
        /** 이 면접관의 합불: PASS, FAIL, 정하지 않았으면 "" (사용자 2026-10-28) */
        private String decision = "";
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
        certs = certs == null ? new ArrayList<>() : new ArrayList<>(certs.stream().filter(r -> r != null).toList());
        certs.forEach(r -> {
            r.setLabel(r.getLabel() == null ? "" : r.getLabel().strip());
            r.setValue(r.getValue() == null ? "" : r.getValue().strip());
        });
        qa = qa == null ? new ArrayList<>() : new ArrayList<>(qa.stream().filter(q -> q != null).toList());
        qaOther = qaOther == null ? null : new ArrayList<>(qaOther.stream().filter(q -> q != null).toList());
        opinions = opinions == null ? new ArrayList<>() : new ArrayList<>(opinions.stream().filter(o -> o != null).toList());
        opinions.forEach(o -> {
            o.setTeam(o.getTeam() == null ? "" : o.getTeam().strip());
            o.setName(o.getName() == null ? "" : o.getName().strip());
            o.setContent(o.getContent() == null ? "" : o.getContent().strip());
            o.setDecision("PASS".equals(o.getDecision()) || "FAIL".equals(o.getDecision()) ? o.getDecision() : "");
        });
        reportDate = reportDate == null ? "" : reportDate.strip();
        interviewDate = interviewDate == null ? "" : interviewDate.strip();
        position = position == null ? null : position.strip();
        qaSource = SHEET.equals(qaSource) ? SHEET : RECORD;
    }
}
