package com.example.interview.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** 보고서 지원자 정보 중 면접에서만 알 수 있는 것. 찾지 못하면 빈 문자열 */
@Data
public class ReportInfoResult {

    /** 파견 가능 여부 */
    private String dispatch;
    /** 입사 가능 시기 */
    @JsonProperty("start_date")
    private String startDate;
    /** 희망연봉 */
    @JsonProperty("desired_salary")
    private String desiredSalary;

    public void validate() {
        dispatch = dispatch == null ? "" : dispatch.strip();
        startDate = startDate == null ? "" : startDate.strip();
        desiredSalary = desiredSalary == null ? "" : desiredSalary.strip();
    }
}
