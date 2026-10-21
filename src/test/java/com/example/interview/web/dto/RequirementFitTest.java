package com.example.interview.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.dto.CareerEvalResult;

class RequirementFitTest {

    @Test
    void careerComparesRelevantCareerWhenCalculated() {
        assertThat(RequirementFit.career("경력 3년 이상", new CareerGauge(40, 36, 30))).isFalse();
        assertThat(RequirementFit.career("경력 3년 이상", new CareerGauge(40, 36, 36))).isTrue();
        assertThat(RequirementFit.career("경력 3년 이상", new CareerGauge(40, 36, null))).as("검사 전에는 총 경력").isTrue();
        assertThat(RequirementFit.career("경력무관", null)).isTrue();
        assertThat(RequirementFit.career("신입·경력", null)).isTrue();
        assertThat(RequirementFit.career("경력 3년 이상", null)).isNull();
        assertThat(RequirementFit.career("", null)).isNull();
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
