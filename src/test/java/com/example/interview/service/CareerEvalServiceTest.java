package com.example.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.CandidateProfile;

class CareerEvalServiceTest {

    @Test
    void educationCallSendsSchoolsAndConditionOnly() {
        assertThat(CareerEvalService.educationContent("초대졸 이상", "", profile())).isEqualTo("""
                <education_requirement>초대졸 이상</education_requirement>

                <schools>
                한결대학교 | 중퇴 | 2013.03~2020.03
                </schools>""");
        assertThat(CareerEvalService.educationContent("", "", profile())).doesNotContain("<education_requirement>");
        // 요구 학과가 있을 때만 전공을 보낸다
        assertThat(CareerEvalService.educationContent("", "컴퓨터공학", profile())).isEqualTo("""
                <major_requirement>컴퓨터공학</major_requirement>

                <schools>
                한결대학교 | 시각디자인 | 중퇴 | 2013.03~2020.03
                </schools>""");
    }

    @Test
    void educationCallAddsTheResumeSummaryWhenThereIsOne() {
        CandidateProfile p = profile();
        p.setEducationNotes(List.of("대학교(4년) 졸업", "인정학력 4년제"));
        assertThat(CareerEvalService.educationContent("대졸(4년제) 이상", "", p)).endsWith("""
                </schools>

                <education_summary>
                대학교(4년) 졸업
                인정학력 4년제
                </education_summary>""");
    }

    @Test
    void timelineUsesEvidencePeriodsAndReadsOnlyTheMissingOnesFromTheLocalProfile() {
        // 근거 분석이 A사·학교1을 읽었다. B사·교육1·활동1은 답에 없어 로컬 AI가 옮겨 둔 기간을 코드로 읽는다. 모르는 이름표는 버린다
        List<CareerEvalResult.Period> fromEvidence = List.of(period("[A사]", "2026-06", "2026-10"), period("학교1", "2013-03", "2014-06"),
                period("A사", "2020-01", "2020-02"), period("Z사", "2019-01", "2019-02"));

        List<CareerEvalResult.Period> timeline = CareerEvalService.timeline(profile(), fromEvidence, YearMonth.of(2026, 10));

        assertThat(timeline).extracting("ref", "start", "end").containsExactly(
                org.assertj.core.groups.Tuple.tuple("A사", "2026-06", "2026-10"),
                org.assertj.core.groups.Tuple.tuple("학교1", "2013-03", "2014-06"),
                org.assertj.core.groups.Tuple.tuple("B사", "2024-08", "2026-05"),
                org.assertj.core.groups.Tuple.tuple("교육1", "2023-01", "2023-06"),
                org.assertj.core.groups.Tuple.tuple("활동1", "2014-07", "2016-04"));
        // 근거 분석이 실패했으면 이전 타임라인을 그대로 둔다
        assertThat(CareerEvalService.timeline(profile(), null, YearMonth.of(2026, 10))).isNull();
    }

    @Test
    void readsLocalPeriodTexts() {
        YearMonth now = YearMonth.of(2026, 11);
        assertThat(range("2026.06 ~ 재직중", now)).isEqualTo("2026-06~2026-11");
        assertThat(range("2024년 8월 ~ 2026년 5월", now)).isEqualTo("2024-08~2026-05");
        assertThat(range("2024.08.21 ~ 2025.05.31 (10개월)", now)).isEqualTo("2024-08~2025-05");
        assertThat(range("2019 ~ 2020", now)).isEqualTo("2019-01~2020-12");
        assertThat(range("2023.03", now)).isEqualTo("2023-03~2023-03");
        assertThat(range("2024-01 ~ 현재", now)).isEqualTo("2024-01~2026-11");
        assertThat(CareerEvalService.parsePeriod("A사", "1년 11개월", now)).isNull();
        assertThat(CareerEvalService.parsePeriod("A사", "2026.06 ~ 2024.01", now)).as("끝이 시작보다 앞이면 버린다").isNull();
    }

    private static String range(String text, YearMonth now) {
        CareerEvalResult.Period p = CareerEvalService.parsePeriod("A사", text, now);
        return p == null ? null : p.getStart() + "~" + p.getEnd();
    }

    private static CareerEvalResult.Period period(String ref, String start, String end) {
        CareerEvalResult.Period p = new CareerEvalResult.Period();
        p.setRef(ref);
        p.setStart(start);
        p.setEnd(end);
        return p;
    }

    @Test
    void readsOldResultsThatStillHaveSalaries() {
        CareerEvalResult r = CareerEvalService.parse("{\"total_months\":5,\"salaries\":[{\"text\":\"3,400만원\",\"values\":[3400]}]}");
        assertThat(r).isNotNull().extracting("totalMonths").isEqualTo(5);
    }

    @Test
    void aliasesCompanies() {
        assertThat(CareerEvalService.alias(0)).isEqualTo("A사");
        assertThat(CareerEvalService.alias(25)).isEqualTo("Z사");
        assertThat(CareerEvalService.alias(26)).isEqualTo("회사27");
    }

    private static CandidateProfile profile() {
        CandidateProfile p = new CandidateProfile();
        CandidateProfile.Career c1 = new CandidateProfile.Career();
        c1.setAlias("A사");
        c1.setCompany("(주)다온테크");
        c1.setDepartment("연구개발팀");
        c1.setPosition("대리");
        c1.setPeriod("2026.06~재직중");
        c1.setSalary("3,400만원");
        CandidateProfile.Career c2 = new CandidateProfile.Career();
        c2.setAlias("B사");
        c2.setCompany("누리페이");
        c2.setDepartment("기술연구소");
        c2.setPeriod("2024년 8월 ~ 2026년 5월");
        p.setCareers(List.of(c1, c2));
        CandidateProfile.Education e = new CandidateProfile.Education();
        e.setSchool("한결대학교");
        e.setMajor("시각디자인");
        e.setStatus("중퇴");
        e.setPeriod("2013.03~2020.03");
        e.setGpa("3.19/4.5");
        e.setAlias("학교1");
        p.setEducation(List.of(e));
        CandidateProfile.Training t = new CandidateProfile.Training();
        t.setName("자바 웹 개발자 과정");
        t.setInstitution("미래정보교육원");
        t.setPeriod("2023.01~2023.06");
        t.setAlias("교육1");
        p.setTrainings(List.of(t));
        CandidateProfile.Activity a = new CandidateProfile.Activity();
        a.setKind("병역");
        a.setName("육군 병장 만기전역");
        a.setPeriod("2014.07~2016.04");
        a.setAlias("활동1");
        p.setActivities(List.of(a));
        return p;
    }
}
