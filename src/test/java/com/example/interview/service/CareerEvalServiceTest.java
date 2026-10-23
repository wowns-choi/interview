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
    void timelineCallSendsOnlyLabelsAndPeriods() {
        assertThat(CareerEvalService.timelineContent(CareerEvalService.periodLines(profile()), YearMonth.of(2026, 10)))
                .isEqualTo("""
                        기준 월: 2026-10

                        <periods>
                        A사 | 2026.06~재직중
                        B사 | 2024년 8월 ~ 2026년 5월
                        학교1 | 2013.03~2020.03
                        교육1 | 2023.01~2023.06
                        활동1 | 2014.07~2016.04
                        </periods>""");
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
