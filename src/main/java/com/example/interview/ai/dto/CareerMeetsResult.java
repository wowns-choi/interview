package com.example.interview.ai.dto;

import java.util.List;

import com.example.interview.ai.AiResponseInvalidException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** 관련 경력 개월과 공고 경력 조건으로 판단한 경력 조건 충족 여부 (CAREER_MEETS) */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CareerMeetsResult {

    /** MET, NOT_MET, UNKNOWN */
    @JsonProperty("career_meets")
    private String careerMeets;

    @JsonProperty("career_reason")
    private String careerReason;

    public void validate() {
        if (careerMeets == null || !List.of("MET", "NOT_MET", "UNKNOWN").contains(careerMeets)) {
            throw new AiResponseInvalidException("career_meets는 MET, NOT_MET, UNKNOWN 중 하나여야 함");
        }
        careerReason = careerReason == null ? "" : careerReason;
    }
}
