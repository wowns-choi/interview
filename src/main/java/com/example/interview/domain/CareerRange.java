package com.example.interview.domain;

/**
 * 공고 요구 경력을 개월로 정해 둔 것. 원문은 화면에만 쓰고, 충족 여부는 이 값과 근거 분석이 센 관련 경력 개월로 코드가 정한다
 * (사용자 2026-11-09: 원문을 정규식으로 다시 읽거나 GPT에 따로 묻지 않는다). 하한·상한이 다 null이면 경력무관.
 */
public final class CareerRange {

    private CareerRange() {
    }

    /** 경력 개월이 조건을 채우는지. 개월을 모르면 null */
    public static Boolean meets(Integer min, Integer max, Integer months) {
        if (min == null && max == null) {
            return Boolean.TRUE;
        }
        if (months == null) {
            return null;
        }
        return (min == null || months >= min) && (max == null || months <= max);
    }

    /** "36개월 이상", "36개월 이하", "24~60개월", "경력무관" */
    public static String describe(Integer min, Integer max) {
        if (min != null && max != null) {
            return min + "~" + max + "개월";
        }
        if (min != null) {
            return min + "개월 이상";
        }
        if (max != null) {
            return max + "개월 이하";
        }
        return "경력무관";
    }

    /** 조건 편집기의 선택 (ANY 경력무관, MIN N년 이상, MAX N년 이하) → {하한, 상한} 개월. 그 밖이면 둘 다 null */
    public static Integer[] fromChoice(String kind, String years) {
        Integer months = null;
        try {
            months = years == null || years.isBlank() ? null : Integer.parseInt(years.strip()) * 12;
        } catch (NumberFormatException e) {
            months = null;
        }
        if ("MIN".equals(kind) && months != null) {
            return new Integer[] { months, null };
        }
        if ("MAX".equals(kind) && months != null) {
            return new Integer[] { null, months };
        }
        return new Integer[] { null, null };
    }
}
