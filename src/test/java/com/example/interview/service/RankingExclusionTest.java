package com.example.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.interview.domain.Position;
import com.example.interview.web.dto.FitScore;

class RankingExclusionTest {

    @Test
    void excludesOnlyUnmetConditionsThePositionTurnedOn() {
        FitScore careerUnmet = new FitScore(0, 1, List.of());
        FitScore bothUnmet = new FitScore(0, 0, List.of());

        assertThat(RankingService.exclusion(position(false, false), bothUnmet)).isNull();
        assertThat(RankingService.exclusion(position(true, false), careerUnmet)).isEqualTo("경력 미충족");
        assertThat(RankingService.exclusion(position(false, true), careerUnmet)).isNull();
        assertThat(RankingService.exclusion(position(true, true), bothUnmet)).isEqualTo("경력·학력 미충족");
        assertThat(RankingService.exclusion(position(true, true), null)).as("검사 전").isNull();
    }

    private static Position position(boolean career, boolean education) {
        Position p = new Position();
        p.setExcludeCareer(career);
        p.setExcludeEducation(education);
        return p;
    }
}
