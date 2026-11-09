package com.example.interview.ai.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.AiResponseInvalidException;

class CriteriaResultTest {

    @Test
    void experienceMonthsMustBeAValidRange() {
        assertThatCode(() -> result("신입·경력 3년 ↓", 0, 36).validate()).doesNotThrowAnyException();
        assertThatCode(() -> result("경력무관", null, null).validate()).doesNotThrowAnyException();
        assertThatThrownBy(() -> result("경력 2~5년", 60, 24).validate()).isInstanceOf(AiResponseInvalidException.class);
        assertThatThrownBy(() -> result("경력 3년 이상", -1, null).validate()).isInstanceOf(AiResponseInvalidException.class);
        assertThatThrownBy(() -> result("", 36, null).validate()).isInstanceOf(AiResponseInvalidException.class);
    }

    private static CriteriaResult result(String experience, Integer min, Integer max) {
        CriteriaResult r = new CriteriaResult();
        r.setRequiredExperience(experience);
        r.setExperienceMinMonths(min);
        r.setExperienceMaxMonths(max);
        r.setRequiredEducation("");
        CriteriaResult.Item item = new CriteriaResult.Item();
        item.setName("Java 경험");
        item.setCategory("TECH");
        item.setResumeAssessable(true);
        r.setCriteria(List.of(item));
        return r;
    }
}
