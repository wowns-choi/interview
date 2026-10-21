package com.example.interview.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * 로컬 AI가 이력서에서 옮겨 적은 인적사항·경력 정보·학력·교육·자격증·연봉. 값은 원문 표기 그대로이고, 없으면 빈 문자열이다.
 * 화면에만 보여 주고 외부 AI에는 보내지 않는다 (이력서 글에서는 이 값들이 모두 가려진다).
 */
@Data
public class CandidateProfile {

    private String gender = "";
    /** 생년월일이나 나이 */
    private String birth = "";
    private String phone = "";
    private String email = "";
    private String address = "";
    private String link = "";
    private List<Education> education = new ArrayList<>();
    private List<Career> careers = new ArrayList<>();
    private List<Training> trainings = new ArrayList<>();
    private List<Certificate> certificates = new ArrayList<>();
    /** 회사·학교·교육 밖의 기간 있는 활동 (병역, 연수, 인턴, 대외활동, 회사 밖 프로젝트) */
    private List<Activity> activities = new ArrayList<>();
    @JsonProperty("total_career")
    private String totalCareer = "";
    @JsonProperty("last_salary")
    private String lastSalary = "";
    @JsonProperty("desired_salary")
    private String desiredSalary = "";

    @Data
    public static class Education {
        /** 외부 AI에 학교 대신 쓰는 이름표 ("학교1"). 업로드 때 코드가 목록 순서로 정한다 */
        private String alias = "";
        private String school = "";
        private String major = "";
        private String status = "";
        private String period = "";
        private String gpa = "";

        @JsonIgnore
        public boolean isBlank() {
            return allBlank(school, major, status, period, gpa);
        }
    }

    @Data
    public static class Career {
        /** 이력서 글과 외부 AI에 회사 이름 대신 쓰는 이름표 ("A사"). 업로드 때 코드가 경력 목록 순서로 정한다 */
        private String alias = "";
        private String company = "";
        private String department = "";
        private String position = "";
        private String period = "";
        /** 근무 기간 길이 ("1년 11개월") */
        private String duration = "";
        private String salary = "";

        @JsonIgnore
        public boolean isBlank() {
            return allBlank(company, department, position, period, duration, salary);
        }
    }

    @Data
    public static class Training {
        /** 외부 AI에 교육 대신 쓰는 이름표 ("교육1") */
        private String alias = "";
        private String name = "";
        private String institution = "";
        private String period = "";

        @JsonIgnore
        public boolean isBlank() {
            return allBlank(name, institution, period);
        }
    }

    @Data
    public static class Certificate {
        private String name = "";
        private String issuer = "";
        private String date = "";

        @JsonIgnore
        public boolean isBlank() {
            return allBlank(name, issuer, date);
        }
    }

    @Data
    public static class Activity {
        /** 외부 AI에 활동 대신 쓰는 이름표 ("활동1") */
        private String alias = "";
        /** 예: "병역", "어학연수", "인턴", "개인 프로젝트" */
        private String kind = "";
        private String name = "";
        private String period = "";

        @JsonIgnore
        public boolean isBlank() {
            return allBlank(period);
        }

        /** 병역은 외부 AI에 보내는 이력서 글에서 가린다 */
        @JsonIgnore
        public boolean isMilitary() {
            return (kind + name).matches("(?s).*(병역|군\\s*복무|입대|전역|제대|육군|해군|공군|해병|사회복무|산업기능|전문연구).*");
        }
    }

    @JsonIgnore
    public boolean isFilled() {
        return !(education.isEmpty() && careers.isEmpty() && trainings.isEmpty() && certificates.isEmpty() && activities.isEmpty()
                && !isPersonFilled() && allBlank(totalCareer, lastSalary, desiredSalary));
    }

    /** 성별·나이·연락처 줄을 보여 줄지 */
    @JsonIgnore
    public boolean isPersonFilled() {
        return !allBlank(gender, birth, phone, email, address, link);
    }

    static boolean allBlank(String... values) {
        return Stream.of(values).allMatch(v -> v == null || v.isBlank());
    }
}
