package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.List;

import com.example.interview.ai.AiResponseInvalidException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** 학교 목록과 공고 학력 조건으로 판단한 최종 학력과 충족 여부 (EDUCATION_EVAL) */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EducationResult {

    /** 예: "4년제 대학교 졸업". 학력 정보가 없으면 빈 문자열 */
    @JsonProperty("final_education")
    private String finalEducation;

    @JsonProperty("education_reason")
    private String educationReason;

    /** MET, NOT_MET, UNKNOWN */
    @JsonProperty("education_meets")
    private String educationMeets;

    public void validate() {
        List<String> errors = new ArrayList<>();
        if (finalEducation == null) {
            errors.add("final_education 누락 (학력 정보가 없으면 빈 문자열)");
        }
        if (educationMeets == null || !List.of("MET", "NOT_MET", "UNKNOWN").contains(educationMeets)) {
            errors.add("education_meets는 MET, NOT_MET, UNKNOWN 중 하나여야 함");
        }
        if (!errors.isEmpty()) {
            throw new AiResponseInvalidException(String.join("; ", errors));
        }
        educationReason = educationReason == null ? "" : educationReason;
    }
}
