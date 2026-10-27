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

    @Test
    void careerUsesAiVerdictOnlyForTheSameRequirement() {
        CareerEvalResult eval = new CareerEvalResult();
        eval.setCareerMeets("MET");
        eval.setCareerRequirement("신입·경력 3년 ↓");
        CareerGauge over = new CareerGauge(48, 36, 48);
        assertThat(RequirementFit.career("신입·경력 3년 ↓", over, eval)).as("외부 AI 판단이 코드 비교보다 먼저").isTrue();
        assertThat(RequirementFit.career("경력 5년 이상", over, eval)).as("조건이 바뀌면 코드로").isFalse();
        eval.setCareerMeets("UNKNOWN");
        assertThat(RequirementFit.career("신입·경력 3년 ↓", over, eval)).isNull();
        assertThat(RequirementFit.career("신입·경력 3년 ↓", over, null)).isFalse();
    }
}
