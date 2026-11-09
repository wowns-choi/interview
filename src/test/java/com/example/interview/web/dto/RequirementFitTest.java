package com.example.interview.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.dto.CareerEvalResult;

class RequirementFitTest {

    @Test
    void careerReadsTheVerdictStoredAtTheCheck() {
        CareerEvalResult eval = new CareerEvalResult();
        eval.setCareerMeets("MET");
        assertThat(RequirementFit.career(eval)).isTrue();
        eval.setCareerMeets("NOT_MET");
        assertThat(RequirementFit.career(eval)).isFalse();
        eval.setCareerMeets("UNKNOWN");
        assertThat(RequirementFit.career(eval)).as("조건 없음").isNull();
        eval.setCareerMeets(null);
        assertThat(RequirementFit.career(eval)).isNull();
        assertThat(RequirementFit.career(null)).isNull();
    }

    @Test
    void educationUsesExternalAiJudgment() {
        CareerEvalResult eval = new CareerEvalResult();
        eval.setEducationMeets("NOT_MET");
        assertThat(RequirementFit.education(eval)).isFalse();
        eval.setEducationMeets("UNKNOWN");
        assertThat(RequirementFit.education(eval)).isNull();
        assertThat(RequirementFit.education(null)).isNull();
    }
}
