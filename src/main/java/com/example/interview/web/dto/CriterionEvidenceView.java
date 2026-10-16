package com.example.interview.web.dto;

import java.util.List;

import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.EvidenceType;

import lombok.Value;

/** 평가항목 하나와 그 근거들 (직접 → 간접 → 없음 순) */
@Value
public class CriterionEvidenceView {
    EvalCriterion criterion;
    List<Evidence> evidences;
    String interviewPoint;

    /** 이 평가항목으로 검사한 적이 있는지 (검사 뒤에 추가한 항목은 false) */
    public boolean isChecked() {
        return !evidences.isEmpty();
    }

    /** 가장 강한 근거 유형. 근거가 없으면 NONE */
    public EvidenceType getMatch() {
        EvidenceType best = EvidenceType.NONE;
        for (Evidence e : evidences) {
            if (e.getEvidenceType() == EvidenceType.DIRECT) {
                return EvidenceType.DIRECT;
            }
            if (e.getEvidenceType() == EvidenceType.INDIRECT) {
                best = EvidenceType.INDIRECT;
            }
        }
        return best;
    }

    /** 인용이 있는 근거 (근거 없음 항목 제외) */
    public List<Evidence> getQuotedEvidences() {
        return evidences.stream().filter(e -> e.getQuote() != null && !e.getQuote().isBlank()).toList();
    }

    /** 근거 없음일 때 AI가 남긴 설명 */
    public String getNoneNote() {
        return evidences.stream()
                .filter(e -> e.getEvidenceType() == EvidenceType.NONE)
                .map(Evidence::getInterpretation)
                .filter(s -> s != null && !s.isBlank())
                .findFirst().orElse(null);
    }
}
