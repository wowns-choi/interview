package com.example.interview.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * 적합도 검사 때 외부 AI가 계산한 경력·학력·타임라인. candidate.career_eval_json에 저장한다.
 * 학력은 학력 호출(EDUCATION_EVAL), 타임라인은 타임라인 호출(TIMELINE)에서,
 * 총 경력과 관련 경력은 가린 이력서 글과 함께 보낸 근거 분석 호출(EVIDENCE)의 career에서 채운다.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CareerEvalResult {

    /** 회사별 개월과 관련 개월 (계산 과정을 남기려고 받는다) */
    @JsonProperty("per_company")
    private List<Company> perCompany;

    @JsonProperty("total_months")
    private Integer totalMonths;

    @JsonProperty("total_reason")
    private String totalReason;

    /** 총 경력 중 이 포지션의 경력으로 볼 수 있는 개월 */
    @JsonProperty("relevant_months")
    private Integer relevantMonths;

    @JsonProperty("relevant_reason")
    private String relevantReason;

    /** 예: "4년제 대학교 졸업". 학력 정보가 없으면 빈 문자열 */
    @JsonProperty("final_education")
    private String finalEducation;

    @JsonProperty("education_reason")
    private String educationReason;

    /** 판단에 쓴 포지션의 학력 조건. 없으면 빈 문자열 */
    @JsonProperty("required_education")
    private String requiredEducation;

    /** MET, NOT_MET, UNKNOWN (공고에 학력 조건이 없거나 판단할 수 없음) */
    @JsonProperty("education_meets")
    private String educationMeets;

    /** 보낸 항목(A사, 학교1, 교육1, 활동1)마다 기간을 YYYY-MM으로 읽은 것 */
    private List<Period> timeline;

    @Data
    public static class Period {
        private String ref;
        private String start;
        private String end;
    }

    @Data
    public static class Company {
        private String company;
        private Integer months;
        @JsonProperty("relevant_months")
        private Integer relevantMonths;
        private String reason;
    }

    /** 근거 분석 호출에서 받은 경력 계산을 넣는다 */
    public void applyCareer(EvidenceResult.Career career) {
        perCompany = career.getPerCompany();
        totalMonths = career.getTotalMonths();
        totalReason = career.getTotalReason();
        relevantMonths = career.getRelevantMonths();
        relevantReason = career.getRelevantReason();
    }
}
