package com.example.interview.web.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.CandidateProfile;

class TimelineViewTest {

    @Test
    void mapsAliasesBackToNamesAndMarksGapsOfThreeMonthsOrMore() {
        CandidateProfile p = new CandidateProfile();
        CandidateProfile.Career c1 = new CandidateProfile.Career();
        c1.setCompany("다온테크");
        c1.setDepartment("연구개발팀");
        CandidateProfile.Career c2 = new CandidateProfile.Career();
        c2.setCompany("누리페이");
        p.setCareers(List.of(c1, c2));
        CandidateProfile.Education e = new CandidateProfile.Education();
        e.setSchool("한결대학교");
        e.setStatus("졸업");
        p.setEducation(List.of(e));
        CandidateProfile.Activity a = new CandidateProfile.Activity();
        a.setKind("병역");
        a.setName("육군 만기전역");
        p.setActivities(List.of(a));

        CareerEvalResult eval = new CareerEvalResult();
        eval.setTimeline(List.of(period("학교1", "2020-03", "2024-02"), period("B사", "2024-08", "2026-05"),
                period("A사", "2026-06", "2026-10"), period("활동1", "2021-01", "2022-06"),
                period("교육9", "2019-01", "2019-12")));

        TimelineView t = TimelineView.of(aliased(p), eval, YearMonth.of(2026, 10));

        assertThat(t.getLanes()).extracting("name").containsExactly("경력", "학력", "활동");
        assertThat(t.getLanes().get(0).getRows()).hasSize(1);
        assertThat(t.getLanes().get(0).getRows().get(0)).extracting("label", "detail", "periodText")
                .containsExactly(tuple("누리페이", "", "2024.08 ~ 2026.05"), tuple("다온테크", "연구개발팀", "2026.06 ~ 2026.10"));
        assertThat(t.getLanes().get(2).getRows().get(0).get(0).getLabel()).isEqualTo("육군 만기전역");
        // 2020.03 ~ 2026.10 = 80개월. 2024.03 ~ 2024.07(5개월)만 빈다
        assertThat(t.getGaps()).extracting("periodText", "lengthText").containsExactly(tuple("2024.03 ~ 2024.07", "5개월"));
        assertThat(t.getGaps().get(0).getLeft()).isEqualTo(60.0);
        assertThat(t.getTicks()).extracting("year").containsExactly(2021, 2022, 2023, 2024, 2025, 2026);
    }

    @Test
    void overlappingItemsInOneLaneGoToSeparateRowsAndShortGapsAreIgnored() {
        CandidateProfile p = new CandidateProfile();
        p.setCareers(List.of(new CandidateProfile.Career(), new CandidateProfile.Career()));
        CareerEvalResult eval = new CareerEvalResult();
        eval.setTimeline(List.of(period("A사", "2024-01", "2024-12"), period("B사", "2024-06", "2025-03"),
                period("A사", "2025-06", "2025-08")));

        TimelineView t = TimelineView.of(aliased(p), eval, YearMonth.of(2025, 8));

        assertThat(t.getLanes().get(0).getRows()).hasSize(2);
        assertThat(t.getGaps()).as("2025.04 ~ 2025.05는 2개월").isEmpty();
    }

    @Test
    void widensSoLabelsFitAndPutsTooShortBarLabelsBesideTheBar() {
        CandidateProfile p = new CandidateProfile();
        CandidateProfile.Career c1 = new CandidateProfile.Career();
        c1.setCompany("다온테크");
        CandidateProfile.Career c2 = new CandidateProfile.Career();
        c2.setCompany("누리페이");
        p.setCareers(List.of(c1, c2));
        CandidateProfile.Education e = new CandidateProfile.Education();
        e.setSchool("한결대학교 사범대학 부속 고등학교");
        p.setEducation(List.of(e));
        CareerEvalResult eval = new CareerEvalResult();
        // 2009.03 ~ 2026.10 = 212개월. A사 1개월은 최대 폭으로도 안 들어가 이름을 옆(오른쪽 끝이라 왼쪽)에 쓴다
        eval.setTimeline(List.of(period("학교1", "2009-03", "2012-02"), period("B사", "2024-08", "2026-05"),
                period("A사", "2026-06", "2026-06")));

        TimelineView t = TimelineView.of(aliased(p), eval, YearMonth.of(2026, 10));

        TimelineView.Bar school = t.getLanes().get(1).getRows().get(0).get(0);
        assertThat(school.getSide()).isNull();
        assertThat(TimelineView.need(school.getLabel(), school.getPeriodText()))
                .isLessThanOrEqualTo(school.getWidth() / 100 * t.getWidth());
        assertThat(t.getWidth()).isBetween(TimelineView.MIN_WIDTH, TimelineView.MAX_WIDTH);
        List<List<TimelineView.Bar>> careers = t.getLanes().get(0).getRows();
        assertThat(careers).as("옆에 쓴 이름이 B사 막대와 겹치므로 줄을 나눈다").hasSize(2);
        assertThat(careers.get(1).get(0)).extracting("label", "side").containsExactly("다온테크", "left");
    }

    @Test
    void fiveMonthJobOnThirteenYearTimelineStillGetsItsLabelInside() {
        CandidateProfile p = new CandidateProfile();
        CandidateProfile.Career c1 = new CandidateProfile.Career();
        c1.setCompany("(주)태화이노베이션");
        p.setCareers(List.of(c1));
        CandidateProfile.Training tr = new CandidateProfile.Training();
        tr.setName("6개월 국비 교육과정 수료");
        tr.setInstitution("");
        p.setTrainings(List.of(tr));
        CandidateProfile.Education e = new CandidateProfile.Education();
        e.setSchool("고등학교");
        p.setEducation(List.of(e));
        CareerEvalResult eval = new CareerEvalResult();
        eval.setTimeline(List.of(period("학교1", "2013-03", "2016-02"), period("교육1", "2024-01", "2024-06"),
                period("A사", "2026-06", "2026-10")));

        TimelineView t = TimelineView.of(aliased(p), eval, YearMonth.of(2026, 10));

        assertThat(t.getLanes()).allSatisfy(lane -> assertThat(lane.getRows()).allSatisfy(row ->
                assertThat(row).allSatisfy(bar -> assertThat(bar.getSide()).as(bar.getLabel()).isNull())));
    }

    @Test
    void nullWithoutPeriods() {
        assertThat(TimelineView.of(new CandidateProfile(), null, YearMonth.of(2026, 10))).isNull();
        CareerEvalResult eval = new CareerEvalResult();
        eval.setTimeline(List.of(period("A사", "2024-01", "2024-12")));
        assertThat(TimelineView.of(new CandidateProfile(), eval, YearMonth.of(2026, 10))).as("모르는 이름표뿐").isNull();
    }

    /** 업로드 때처럼 목록 순서로 별칭을 붙인다 */
    private static CandidateProfile aliased(CandidateProfile p) {
        for (int i = 0; i < p.getCareers().size(); i++) {
            p.getCareers().get(i).setAlias((char) ('A' + i) + "사");
        }
        for (int i = 0; i < p.getEducation().size(); i++) {
            p.getEducation().get(i).setAlias("학교" + (i + 1));
        }
        for (int i = 0; i < p.getTrainings().size(); i++) {
            p.getTrainings().get(i).setAlias("교육" + (i + 1));
        }
        for (int i = 0; i < p.getActivities().size(); i++) {
            p.getActivities().get(i).setAlias("활동" + (i + 1));
        }
        return p;
    }

    private static CareerEvalResult.Period period(String ref, String start, String end) {
        CareerEvalResult.Period p = new CareerEvalResult.Period();
        p.setRef(ref);
        p.setStart(start);
        p.setEnd(end);
        return p;
    }
}
