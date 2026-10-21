package com.example.interview.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.AiResponseInvalidException;

class EvidenceResultTest {

    @Test
    void careerCalculationIsRequired() {
        EvidenceResult r = result(null);
        assertThatThrownBy(() -> r.validate(Set.of(1L))).isInstanceOf(AiResponseInvalidException.class)
                .hasMessageContaining("career 누락");

        EvidenceResult tooMuch = result(career(10, 12));
        assertThatThrownBy(() -> tooMuch.validate(Set.of(1L))).hasMessageContaining("relevant_months가 total_months보다 큼");

        EvidenceResult ok = result(career(27, 20));
        ok.validate(Set.of(1L));
        assertThat(ok.getCareer().getTotalReason()).isEmpty();
    }

    private static EvidenceResult result(EvidenceResult.Career career) {
        EvidenceResult.Item none = new EvidenceResult.Item();
        none.setType("NONE");
        EvidenceResult.CriterionResult c = new EvidenceResult.CriterionResult();
        c.setCriterionId(1L);
        c.setEvidences(List.of(none));
        EvidenceResult r = new EvidenceResult();
        r.setResults(List.of(c));
        r.setCareer(career);
        return r;
    }

    private static EvidenceResult.Career career(int total, int relevant) {
        EvidenceResult.Career c = new EvidenceResult.Career();
        c.setPerCompany(List.of());
        c.setTotalMonths(total);
        c.setRelevantMonths(relevant);
        return c;
    }
}
