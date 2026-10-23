package com.example.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.interview.domain.QaItem;

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

    @Test
    void reorderKeepsInterviewerQuestionsFirst() {
        List<QaItem> qa = List.of(QaItem.of(QaItem.CANDIDATE, "c", "", ""), QaItem.of(QaItem.INTERVIEWER, "a", "", ""),
                QaItem.of(QaItem.INTERVIEWER, "b", "", ""));
        assertThat(InterviewReportService.reorder(qa, List.of(1, 3, 2))).extracting(QaItem::getQuestion).containsExactly("b", "a", "c");
    }
}
