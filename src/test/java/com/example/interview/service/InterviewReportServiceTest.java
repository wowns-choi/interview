package com.example.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class InterviewReportServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @Test
    void ageFromBirthDateOrAgeText() {
        assertThat(InterviewReportService.age("1995.03.12", TODAY)).isEqualTo("만 31세 (1995년생)");
        assertThat(InterviewReportService.age("1995년 12월 1일", TODAY)).isEqualTo("만 30세 (1995년생)");
        assertThat(InterviewReportService.age("1998년생", TODAY)).isEqualTo("만 27~28세 (1998년생)");
        assertThat(InterviewReportService.age("98년생", TODAY)).isEqualTo("만 27~28세 (1998년생)");
        assertThat(InterviewReportService.age("만 29세", TODAY)).isEqualTo("만 29세");
        assertThat(InterviewReportService.age("", TODAY)).isEmpty();
    }
}
