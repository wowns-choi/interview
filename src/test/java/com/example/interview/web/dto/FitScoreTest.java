package com.example.interview.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.EvidenceType;

class FitScoreTest {

    @Test
    void addsConditionsAndEvidencePointsWithoutMustOrPreferred() {
        CareerEvalResult eval = eval("대졸 이상", "MET", 40);
        eval.setCareerMeets("MET");

        FitScore s = FitScore.of(eval,
                List.of(view("Java", EvidenceType.DIRECT), view("Kafka", EvidenceType.INDIRECT), view("Redis", EvidenceType.NONE)));

        // 1 + 0.5 + 0. 경력·학력은 충족 여부만 들고 점수에는 넣지 않는다
        assertThat(s.getTotal()).isEqualTo(1.5);
        assertThat(s.getTotalText()).isEqualTo("1.5");
        assertThat(s.getMax()).isEqualTo(3);
        assertThat(List.of(s.getCareer(), s.getEducation())).containsExactly(1, 1);
        assertThat(s.isComplete()).isTrue();
        assertThat(s.getDirectCount()).isEqualTo(1);
    }

    @Test
    void missingPostingConditionCountsAsMetMissingResumeInfoCountsAsZero() {
        // 공고에 조건 없음 → 모두 1
        CareerEvalResult noCondition = eval("", "UNKNOWN", null);
        noCondition.setCareerMeets("UNKNOWN");
        FitScore none = FitScore.of(noCondition, List.of());
        assertThat(List.of(none.getCareer(), none.getEducation())).containsExactly(1, 1);

        // 공고에 조건이 있는데 이력서에 정보 없음 → 0
        FitScore missing = FitScore.of(eval("대졸 이상", "UNKNOWN", null), List.of());
        assertThat(List.of(missing.getCareer(), missing.getEducation())).containsExactly(0, 0);
        assertThat(FitScore.education(null)).as("학력 계산 실패").isZero();

        // 학과 조건만 있어도 조건이 있는 것으로 본다
        CareerEvalResult majorOnly = eval("", "NOT_MET", null);
        majorOnly.setRequiredMajor("컴퓨터공학");
        assertThat(FitScore.education(majorOnly)).isZero();
    }

    @Test
    void criteriaAddedAfterTheCheckMakeTheScoreIncomplete() {
        FitScore s = FitScore.of(null,
                List.of(view("Java", EvidenceType.DIRECT), new CriterionEvidenceView(criterion("Kotlin"), List.of(), null)));
        assertThat(s.isComplete()).isFalse();
        assertThat(s.getItems().get(1).getMatch()).isNull();
        assertThat(s.getItems().get(1).getPoints()).isZero();
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
