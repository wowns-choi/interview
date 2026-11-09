package com.example.interview.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.interview.domain.CareerRange;
import com.example.interview.domain.Position;

class ConditionFormTest {

    @Test
    void readsStoredMonthsIntoDropdownChoices() {
        assertThat(form("경력 5년 ↑", 60, null, "학력무관"))
                .extracting("expKind", "expYears", "eduLevel")
                .containsExactly("MIN", 5, "ANY");
        assertThat(form("경력 3년 이하", null, 36, "대졸(4년제) 이상"))
                .extracting("expKind", "expYears", "eduLevel")
                .containsExactly("MAX", 3, "UNIV");
        assertThat(form("신입·경력 3년 ↓", 0, 36, null))
                .extracting("expKind", "expYears").as("신입 가능하면 상한").containsExactly("MAX", 3);
        assertThat(form("경력 2~5년", 24, 60, null))
                .extracting("expKind", "expYears").as("범위는 하한").containsExactly("MIN", 2);
        assertThat(form("신입·경력", null, null, "초대졸(2·3년제) 이상"))
                .extracting("expKind", "expYears", "eduLevel")
                .containsExactly("ANY", null, "COLLEGE");
        assertThat(form(null, null, null, null))
                .extracting("expKind", "eduLevel")
                .containsExactly("", "");
        assertThat(ConditionForm.education("고졸 이상")).isEqualTo("HIGH");
        assertThat(ConditionForm.education("석사 이상")).isEqualTo("MASTER");
        assertThat(ConditionForm.education("박사 이상")).isEqualTo("DOCTOR");
    }

    @Test
    void careerRangeComparesBothBounds() {
        assertThat(CareerRange.meets(null, 36, 24)).isTrue();
        assertThat(CareerRange.meets(null, 36, 48)).isFalse();
        assertThat(CareerRange.meets(36, null, 48)).isTrue();
        assertThat(CareerRange.meets(36, null, 24)).isFalse();
        assertThat(CareerRange.meets(24, 120, 27)).isTrue();
        assertThat(CareerRange.meets(24, 120, 20)).isFalse();
        assertThat(CareerRange.meets(24, 120, 121)).isFalse();
        assertThat(CareerRange.meets(0, 36, 0)).as("신입").isTrue();
        assertThat(CareerRange.meets(null, null, null)).as("경력무관").isTrue();
        assertThat(CareerRange.meets(36, null, null)).as("경력을 못 셈").isNull();
        assertThat(CareerRange.fromChoice("MIN", "3")).containsExactly(36, null);
        assertThat(CareerRange.fromChoice("MAX", "2")).containsExactly(null, 24);
        assertThat(CareerRange.fromChoice("ANY", "")).containsExactly(null, null);
        assertThat(CareerRange.describe(0, 36)).isEqualTo("0~36개월");
    }

    private static ConditionForm form(String experience, Integer min, Integer max, String education) {
        Position p = new Position();
        p.setRequiredExperience(experience);
        p.setCareerMinMonths(min);
        p.setCareerMaxMonths(max);
        p.setRequiredEducation(education);
        return ConditionForm.of(p);
    }
}
