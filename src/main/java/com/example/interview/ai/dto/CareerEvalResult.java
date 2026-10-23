package com.example.interview.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.example.interview.web.dto.CareerGauge;

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

    /** 판단에 쓴 포지션의 요구 학과. 없으면 빈 문자열 */
    @JsonProperty("required_major")
    private String requiredMajor;

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

    /** 근거 분석 호출(EVIDENCE)이 이력서에서 읽은 자격증. 그 호출 전에 검사한 지원자는 null */
    private Certificates certificates;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Certificates {
        /** 이력서에 적힌 자격증·어학 점수 */
        private List<Certificate> items;
        /** 정보처리기사가 있는지 (산업기사·기능사는 아니다) */
        @JsonProperty("info_engineer")
        private Boolean infoEngineer;
        /** SQLD(SQL 개발자)가 있는지 */
        private Boolean sqld;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Certificate {
        private String name;
        private String issuer;
        private String date;
    }

    @Data
    public static class Company {
        private String company;
        private Integer months;
        @JsonProperty("relevant_months")
        private Integer relevantMonths;
        private String reason;
    }

    /**
     * 이 회사(업로드 때 정한 별칭 "A사")를 외부 AI가 경력으로 셌는지. 고객사·프로젝트처럼 경력이 아닌 것을
     * 로컬 AI가 회사로 뽑아도 화면 경력 목록·타임라인에서 뺄 수 있게 한다. 회사별 계산이 없으면 모두 센다.
     */
    public boolean counts(String alias) {
        if (perCompany == null || perCompany.isEmpty() || alias == null) {
            return true;
        }
        String key = bare(alias);
        return perCompany.stream().anyMatch(c -> c.getCompany() != null && bare(c.getCompany()).equals(key));
    }

    /** 이 회사에서 외부 AI가 센 근무 개월 ("5개월"). 이력서에 기간 길이가 없을 때(재직중 등) 화면에 쓴다. 없으면 빈 문자열 */
    public String durationOf(String alias) {
        if (perCompany == null || alias == null) {
            return "";
        }
        String key = bare(alias);
        return perCompany.stream()
                .filter(c -> c.getCompany() != null && c.getMonths() != null && c.getMonths() > 0 && bare(c.getCompany()).equals(key))
                .findFirst().map(c -> CareerGauge.format(c.getMonths())).orElse("");
    }

    private static String bare(String s) {
        return s.replaceAll("[\\[\\]\\s]", "");
    }

    /** 근거 분석 호출에서 받은 경력 계산을 넣는다 */
    public void applyCareer(EvidenceResult.Career career) {
        perCompany = career.getPerCompany();
        totalMonths = career.getTotalMonths();
        totalReason = career.getTotalReason();
        relevantMonths = career.getRelevantMonths();
        relevantReason = career.getRelevantReason();
        certificates = career.getCertificates();
    }
}
