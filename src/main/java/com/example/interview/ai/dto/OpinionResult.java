package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.List;

import com.example.interview.ai.AiResponseInvalidException;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** AI 의견 참고 응답: 이 포지션에 뽑는다면 그 이유, 안 뽑는다면 그 이유. 뽑을지 말지는 정하지 않는다. */
@Data
public class OpinionResult {

    public static final int MAX_POINTS = 5;

    @JsonProperty("hire_reasons")
    private List<Point> hireReasons;
    @JsonProperty("no_hire_reasons")
    private List<Point> noHireReasons;
    /** 물어본 시각 (저장할 때 코드가 채운다) */
    @JsonProperty("asked_at")
    private String askedAt;

    @Data
    public static class Point {
        /** 이유 한 문장 */
        private String point;
        /** 그렇게 본 자료 (이력서 문장, 면접 답변, 판정 등) */
        private String basis;
    }

    public void validate() {
        hireReasons = clean(hireReasons);
        noHireReasons = clean(noHireReasons);
        if (hireReasons.isEmpty() && noHireReasons.isEmpty()) {
            throw new AiResponseInvalidException("hire_reasons와 no_hire_reasons가 모두 비어 있습니다.");
        }
    }

    private static List<Point> clean(List<Point> points) {
        List<Point> kept = new ArrayList<>();
        if (points != null) {
            for (Point p : points) {
                if (p != null && p.point != null && !p.point.isBlank() && kept.size() < MAX_POINTS) {
                    kept.add(p);
                }
            }
        }
        return kept;
    }
}
