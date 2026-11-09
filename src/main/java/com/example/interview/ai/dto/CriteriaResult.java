package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.List;

import com.example.interview.ai.AiResponseInvalidException;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** 호출 ① 평가항목 추출 응답 */
@Data
public class CriteriaResult {

    /** 포지션 제목 (선택). 없으면 정보글 첫 줄을 쓴다 */
    @JsonProperty("position_title")
    private String positionTitle;

    /** 첨부 이미지에서 읽은 공고 내용 (이미지가 없으면 비움) */
    @JsonProperty("image_text")
    private String imageText;

    /** 공고의 요구 경력 표기 그대로 (예: "경력 3년 이상", "신입·경력"). 공고에 없으면 빈 문자열 */
    @JsonProperty("required_experience")
    private String requiredExperience;

    /** 요구 경력 하한 개월 (예: "3년 이상" → 36, "신입" 포함 → 0). 하한이 없으면 null */
    @JsonProperty("experience_min_months")
    private Integer experienceMinMonths;

    /** 요구 경력 상한 개월 (예: "3년 ↓" → 36). 상한이 없으면 null. 둘 다 null이면 경력무관 */
    @JsonProperty("experience_max_months")
    private Integer experienceMaxMonths;

    /** 공고의 학력 조건 표기 그대로 (예: "학력무관", "대졸(4년) 이상"). 공고에 없으면 빈 문자열 */
    @JsonProperty("required_education")
    private String requiredEducation;

    /** 공고의 요구 학과 표기 그대로 (예: "컴퓨터공학 등 관련 학과"). 공고에 없으면 빈 문자열. 빠져도 실패로 보지 않는다 */
    @JsonProperty("required_major")
    private String requiredMajor;

    private List<Item> criteria;

    @Data
    public static class Item {
        private String name;
        private String description;
        private String category;
        @JsonProperty("resume_assessable")
        private Boolean resumeAssessable;
    }

    public void validate() {
        List<String> errors = new ArrayList<>();
        if (requiredExperience == null) {
            errors.add("required_experience 누락 (공고에 없으면 빈 문자열)");
        }
        if (experienceMinMonths != null && experienceMinMonths < 0
                || experienceMaxMonths != null && experienceMaxMonths < 0) {
            errors.add("experience_min_months, experience_max_months는 0 이상의 정수 또는 null");
        } else if (experienceMinMonths != null && experienceMaxMonths != null && experienceMinMonths > experienceMaxMonths) {
            errors.add("experience_min_months가 experience_max_months보다 큼");
        }
        if ((requiredExperience == null || requiredExperience.isBlank())
                && (experienceMinMonths != null || experienceMaxMonths != null)) {
            errors.add("required_experience가 비어 있으면 experience_min_months, experience_max_months도 null");
        }
        if (requiredEducation == null) {
            errors.add("required_education 누락 (공고에 없으면 빈 문자열)");
        }
        if (criteria == null || criteria.isEmpty()) {
            throw new AiResponseInvalidException("criteria 배열이 비어 있습니다.");
        }
        for (int i = 0; i < criteria.size(); i++) {
            Item c = criteria.get(i);
            if (c == null || isBlank(c.name)) {
                errors.add("criteria[" + i + "].name 누락");
                continue;
            }
            if (!List.of("TECH", "EXPERIENCE", "COLLABORATION", "ETC").contains(c.category)) {
                errors.add("criteria[" + i + "].category는 TECH, EXPERIENCE, COLLABORATION, ETC 중 하나여야 함");
            }
            if (c.resumeAssessable == null) {
                errors.add("criteria[" + i + "].resume_assessable 누락");
            }
        }
        if (!errors.isEmpty()) {
            throw new AiResponseInvalidException(String.join("; ", errors));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
