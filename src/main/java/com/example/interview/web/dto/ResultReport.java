package com.example.interview.web.dto;

import java.util.List;
import java.util.Map;

import com.example.interview.domain.Candidate;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.EvidenceType;
import com.example.interview.domain.Position;

import lombok.Value;

/** 결과 화면과 PDF가 함께 쓰는 추출 결과 전체 */
@Value
public class ResultReport {
    Position position;
    Candidate candidate;
    List<CriterionEvidenceView> criteria;
    List<SectionView> sections;
    Map<Long, Evidence> evidenceById;
    AiUsageSummary usage;

    public long getDirectCount() {
        return countMatch(EvidenceType.DIRECT);
    }

    public long getIndirectCount() {
        return countMatch(EvidenceType.INDIRECT);
    }

    public long getNoneCount() {
        return countMatch(EvidenceType.NONE);
    }

    public boolean isFitChecked() {
        return criteria.stream().anyMatch(CriterionEvidenceView::isChecked);
    }

    public boolean isHasUncheckedCriteria() {
        return criteria.stream().anyMatch(c -> !c.isChecked());
    }

    public long getQuestionCount() {
        return sections.stream().mapToLong(s -> s.getQuestions().size()).sum();
    }

    public long getAnsweredCount() {
        return sections.stream().flatMap(s -> s.getQuestions().stream())
                .filter(q -> q.getQuestion().getAnswer() != null && !q.getQuestion().getAnswer().isBlank()).count();
    }

    public long getUnverifiedCount() {
        return evidenceById.values().stream()
                .filter(e -> e.getQuote() != null && !"Y".equals(e.getVerifiedYn())).count();
    }

    private long countMatch(EvidenceType type) {
        return criteria.stream().filter(c -> c.getMatch() == type).count();
    }
}
