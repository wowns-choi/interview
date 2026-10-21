package com.example.interview.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.CandidateProfile;

class CareerGaugeTest {

    @Test
    void readsMonthsFromResumeAndPostingWording() {
        assertThat(CareerGauge.months("총 2년 3개월")).isEqualTo(27);
        assertThat(CareerGauge.months("경력 5년 ↑")).isEqualTo(60);
        assertThat(CareerGauge.months("경력 3~5년")).isEqualTo(36);
        assertThat(CareerGauge.months("1.5년")).isEqualTo(18);
        assertThat(CareerGauge.months("18개월")).isEqualTo(18);
        assertThat(CareerGauge.months("경력 무관")).isNull();
        assertThat(CareerGauge.months("신입")).isNull();
    }

    @Test
    void shortCareerFillsPartOfTheBar() {
        CareerGauge g = CareerGauge.of(profile("총 2년 3개월"), "경력 5년 ↑", null);

        assertThat(g.isOver()).isFalse();
        assertThat(g.getTotalPercent()).isEqualTo(45);
        assertThat(g.getMarkPercent()).isEqualTo(100);
        assertThat(g.getRelevantPercent()).isNull();
    }

    @Test
    void usesExternalAiNumbersAndComparesRelevantCareerWithPosting() {
        CareerGauge g = CareerGauge.of(profile("총 1년"), "5년 이상", eval(96, 72));

        assertThat(g.getActualText()).isEqualTo("8년");
        assertThat(g.getTotalPercent()).isEqualTo(100);
        assertThat(g.getRelevantPercent()).isEqualTo(75);
        assertThat(g.getMarkPercent()).isEqualTo(63);
        assertThat(g.isOver()).isTrue();
        assertThat(g.getDiffText()).isEqualTo("1년");
    }

    @Test
    void barWithoutMarkWhenPostingHasNoYears() {
        CareerGauge g = CareerGauge.of(profile(""), "경력 무관", eval(27, 10));

        assertThat(g.getMarkPercent()).isNull();
        assertThat(g.getTotalPercent()).isEqualTo(100);
        assertThat(g.isOver()).isFalse();
        assertThat(CareerGauge.of(profile(""), "경력 무관", eval(0, 0))).isNull();
    }

    @Test
    void sumsCompanyDurationsWhenTotalIsMissing() {
        CandidateProfile p = profile("");
        p.setCareers(List.of(career("1년 11개월"), career("4개월")));

        CareerGauge g = CareerGauge.of(p, "3년", null);

        assertThat(g.getActualText()).isEqualTo("2년 3개월");
    }

    @Test
    void noBarWithoutNumbers() {
        assertThat(CareerGauge.of(profile("총 2년"), "경력 무관", null)).isNull();
        assertThat(CareerGauge.of(profile(""), "3년", null)).isNull();
        assertThat(CareerGauge.of(null, "3년", null)).isNull();
    }

    private static CareerEvalResult eval(int total, int relevant) {
        CareerEvalResult r = new CareerEvalResult();
        r.setTotalMonths(total);
        r.setRelevantMonths(relevant);
        return r;
    }

    private static CandidateProfile profile(String total) {
        CandidateProfile p = new CandidateProfile();
        p.setTotalCareer(total);
        return p;
    }

    private static CandidateProfile.Career career(String duration) {
        CandidateProfile.Career c = new CandidateProfile.Career();
        c.setDuration(duration);
        return c;
    }
}
