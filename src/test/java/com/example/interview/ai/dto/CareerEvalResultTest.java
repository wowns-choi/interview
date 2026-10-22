package com.example.interview.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CareerEvalResultTest {

    @Test
    void onlyCompaniesTheAiCountedAreCareers() {
        CareerEvalResult r = new CareerEvalResult();
        assertThat(r.counts("C사")).as("회사별 계산 전에는 모두 경력").isTrue();

        CareerEvalResult.Company a = new CareerEvalResult.Company();
        a.setCompany("[A사]");
        CareerEvalResult.Company b = new CareerEvalResult.Company();
        b.setCompany("B사");
        r.setPerCompany(List.of(a, b));

        assertThat(r.counts("A사")).isTrue();
        assertThat(r.counts("B사")).isTrue();
        assertThat(r.counts("C사")).as("고객사처럼 외부 AI가 세지 않은 회사").isFalse();
    }
}
