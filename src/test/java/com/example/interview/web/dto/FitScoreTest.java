package com.example.interview.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.CandidateProfile;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.EvidenceType;
import com.example.interview.domain.Position;

class FitScoreTest {

    @Test
    void addsConditionsAndEvidencePointsWithoutMustOrPreferred() {
        Position p = position("경력 3년 이상");
        CareerEvalResult eval = eval("대졸 이상", "MET", 40);

        FitScore s = FitScore.of(p, eval, CareerGauge.of(new CandidateProfile(), p.getRequiredExperience(), eval),
                List.of(view("Java", EvidenceType.DIRECT), view("Kafka", EvidenceType.INDIRECT), view("Redis", EvidenceType.NONE)));

        // 경력 1 + 학력 1 + 1 + 0.5 + 0 (연봉은 평가하지 않는다)
        assertThat(s.getTotal()).isEqualTo(3.5);
        assertThat(s.getTotalText()).isEqualTo("3.5");
        assertThat(s.getMax()).isEqualTo(5);
        assertThat(s.isComplete()).isTrue();
        assertThat(s.getDirectCount()).isEqualTo(1);
    }

    @Test
    void missingPostingConditionCountsAsMetMissingResumeInfoCountsAsZero() {
        // 공고에 조건 없음 → 모두 1
        FitScore none = FitScore.of(position(null), eval("", "UNKNOWN", null), null, List.of());
        assertThat(List.of(none.getCareer(), none.getEducation())).containsExactly(1, 1);
        assertThat(FitScore.career("경력무관", null)).isEqualTo(1);

        // 공고에 조건이 있는데 이력서에 정보 없음 → 0
        FitScore missing = FitScore.of(position("경력 5년 이상"), eval("대졸 이상", "UNKNOWN", null), null, List.of());
        assertThat(List.of(missing.getCareer(), missing.getEducation())).containsExactly(0, 0);
        assertThat(FitScore.education(null)).as("학력 계산 실패").isZero();
    }

    @Test
    void criteriaAddedAfterTheCheckMakeTheScoreIncomplete() {
        FitScore s = FitScore.of(position(null), null, null,
                List.of(view("Java", EvidenceType.DIRECT), new CriterionEvidenceView(criterion("Kotlin"), List.of(), null)));
        assertThat(s.isComplete()).isFalse();
        assertThat(s.getItems().get(1).getMatch()).isNull();
        assertThat(s.getItems().get(1).getPoints()).isZero();
    }

    private static Position position(String experience) {
        Position p = new Position();
        p.setRequiredExperience(experience);
        return p;
    }

    private static CareerEvalResult eval(String requiredEducation, String meets, Integer relevantMonths) {
        CareerEvalResult e = new CareerEvalResult();
        e.setRequiredEducation(requiredEducation);
        e.setEducationMeets(meets);
        if (relevantMonths != null) {
            e.setTotalMonths(relevantMonths);
            e.setRelevantMonths(relevantMonths);
        }
        return e;
    }

    private static EvalCriterion criterion(String name) {
        EvalCriterion c = new EvalCriterion();
        c.setName(name);
        return c;
    }

    private static CriterionEvidenceView view(String name, EvidenceType type) {
        Evidence e = new Evidence();
        e.setEvidenceType(type);
        return new CriterionEvidenceView(criterion(name), List.of(e), null);
    }
}
