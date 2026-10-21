package com.example.interview.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PiiExtractTest {

    @Test
    void swapsPeriodAndDurationWrittenInTheWrongFields() {
        PiiExtract r = new PiiExtract();
        r.setCareers(new ArrayList<>(List.of(
                job("1년 11개월", "2024.08 ~ 2026.06"),
                job("2026.06 ~ 재직중", "4개월"),
                job("재직중", "총 2년 3개월"))));

        r.validate();

        assertThat(r.getCareers()).extracting("period", "duration").containsExactly(
                tuple("2024.08 ~ 2026.06", "1년 11개월"),
                tuple("2026.06 ~ 재직중", "4개월"),
                tuple("재직중", "총 2년 3개월"));
    }

    @Test
    void masksEveryValueButBuildsProfileFromTheFirstSpelling() {
        PiiExtract r = new PiiExtract();
        r.setNames(List.of("홍길동", "Hong Gildong"));
        r.setSalaries(List.of("3,400 만원"));
        PiiExtract.Job j = job("2024.08 ~ 2026.06", "");
        j.setCompany(List.of("㈜가나소프트", "Gana Soft"));
        j.setDepartment("개발팀");
        r.setCareers(new ArrayList<>(List.of(j)));
        r.validate();

        assertThat(r.toItems()).extracting("type", "text").contains(
                tuple(PiiResult.Type.APPLICANT_NAME, "Hong Gildong"), tuple(PiiResult.Type.COMPANY, "Gana Soft"),
                tuple(PiiResult.Type.SALARY, "3,400 만원"))
                .doesNotContain(tuple(PiiResult.Type.CAREER, "개발팀"));
        assertThat(r.toProfile().getCareers()).extracting("company", "department").containsExactly(tuple("㈜가나소프트", "개발팀"));
        assertThat(r.toProfile().getLastSalary()).isEmpty();
    }

    private static PiiExtract.Job job(String period, String duration) {
        PiiExtract.Job j = new PiiExtract.Job();
        j.setPeriod(period);
        j.setDuration(duration);
        return j;
    }
}
