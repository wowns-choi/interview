package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/** 이름표(A사, 학교1, 교육1, 활동1)마다 기간을 YYYY-MM으로 읽은 것 (TIMELINE) */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TimelineResult {

    private List<CareerEvalResult.Period> timeline;

    /** 기간을 읽지 못한 항목은 뺄 뿐 실패로 보지 않는다 */
    public void validate() {
        timeline = timeline == null ? new ArrayList<>() : new ArrayList<>(timeline.stream()
                .filter(p -> p != null && p.getRef() != null && valid(p.getStart()) && valid(p.getEnd())
                        && p.getStart().compareTo(p.getEnd()) <= 0)
                .toList());
    }

    private static boolean valid(String yearMonth) {
        return yearMonth != null && yearMonth.matches("(19|20)\\d{2}-(0[1-9]|1[0-2])");
    }
}
