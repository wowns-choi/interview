package com.example.interview.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.interview.domain.Position;

class ConditionFormTest {

    @Test
    void readsPostingWordingIntoDropdownChoices() {
        assertThat(form("경력 5년 ↑", "학력무관"))
                .extracting("expKind", "expYears", "eduLevel")
                .containsExactly("MIN", 5, "ANY");
        assertThat(form("경력 3년 이하", "대졸(4년제) 이상"))
                .extracting("expKind", "expYears", "eduLevel")
                .containsExactly("MAX", 3, "UNIV");
        assertThat(form("신입·경력", "초대졸(2·3년제) 이상"))
                .extracting("expKind", "expYears", "eduLevel")
                .containsExactly("ANY", null, "COLLEGE");
        assertThat(form(null, null))
                .extracting("expKind", "eduLevel")
                .containsExactly("", "");
        assertThat(ConditionForm.education("고졸 이상")).isEqualTo("HIGH");
        assertThat(ConditionForm.education("석사 이상")).isEqualTo("MASTER");
        assertThat(ConditionForm.education("박사 이상")).isEqualTo("DOCTOR");
    }

    @Test
    void careerAtMostIsMetOnlyWhenShorter() {
        assertThat(CareerGauge.meets("경력 3년 이하", 36, 24)).isTrue();
        assertThat(CareerGauge.meets("경력 3년 이하", 36, 48)).isFalse();
        assertThat(CareerGauge.meets("경력 3년 이상", 36, 48)).isTrue();
        assertThat(CareerGauge.meets("경력 3년 이상", 36, 24)).isFalse();
    }

    private static ConditionForm form(String experience, String education) {
        Position p = new Position();
        p.setRequiredExperience(experience);
        p.setRequiredEducation(education);
        return ConditionForm.of(p);
    }
}
