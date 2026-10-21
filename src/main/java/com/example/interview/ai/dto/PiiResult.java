package com.example.interview.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/** 가릴 값의 종류와 글자. 로컬 AI 응답(PiiExtract)에서 코드가 만든다 */
public final class PiiResult {

    private PiiResult() {
    }

    public enum Type {
        APPLICANT_NAME, OTHER_NAME, PHONE, EMAIL, RRN, URL, BIRTH, ADDRESS, SENSITIVE, SCHOOL, COMPANY, SALARY,
        /** 재직 기간, 근무 기간 길이, 직급, 총 경력 */
        CAREER,
        /** 학교 이름을 뺀 학력 정보: 전공, 졸업 여부, 재학 기간, 학점, 인정학력 */
        EDUCATION,
        /** 교육 이수 내역: 교육 기관, 과정명, 기간 */
        TRAINING,
        /** 자격증·어학 점수: 이름, 점수, 취득일, 발급처 */
        CERTIFICATE
    }

    @Data
    public static class Item {
        private Type type;
        private String text;
        /** 종류별 기본 표시 대신 쓸 표시 (회사: "[A사]") */
        @JsonIgnore
        private String label;

        public Item() {
        }

        public Item(Type type, String text) {
            this.type = type;
            this.text = text;
        }
    }
}
