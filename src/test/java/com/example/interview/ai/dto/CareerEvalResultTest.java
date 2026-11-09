package com.example.interview.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CareerEvalResultTest {

    @Test
    void certificatesFromTheLocalProfileListAreJudgedInCode() {
        CareerEvalResult.Certificates c = CareerEvalResult.certificatesFromProfile(List.of(
                cert("정보처리산업기사"), cert("SQL 개발자(SQLD)"), cert("정보처리 기사")));
        assertThat(c.getItems()).extracting("name").containsExactly("정보처리산업기사", "SQL 개발자(SQLD)", "정보처리 기사");
        assertThat(c.getInfoEngineer()).as("띄어 쓴 정보처리기사").isTrue();
        assertThat(c.getSqld()).isTrue();

        CareerEvalResult.Certificates none = CareerEvalResult.certificatesFromProfile(List.of(cert("정보처리기능사"), cert("SQLP")));
        assertThat(none.getInfoEngineer()).as("기능사·산업기사는 아니다").isFalse();
        assertThat(none.getSqld()).isFalse();
        assertThat(CareerEvalResult.certificatesFromProfile(null).getItems()).isEmpty();
    }

    private static com.example.interview.domain.CandidateProfile.Certificate cert(String name) {
        com.example.interview.domain.CandidateProfile.Certificate c = new com.example.interview.domain.CandidateProfile.Certificate();
        c.setName(name);
        return c;
    }

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

    @Test
    void durationOfCurrentJobComesFromPerCompany() {
        CareerEvalResult r = new CareerEvalResult();
        CareerEvalResult.Company a = new CareerEvalResult.Company();
        a.setCompany("[A사]");
        a.setMonths(17);
        r.setPerCompany(List.of(a));
        assertThat(r.durationOf("A사")).isEqualTo("1년 5개월");
        assertThat(r.durationOf("B사")).isEmpty();
    }
}
