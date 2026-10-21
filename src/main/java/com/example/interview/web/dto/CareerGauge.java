package com.example.interview.web.dto;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.CandidateProfile;

import lombok.Value;

/**
 * 경력 막대. 막대 끝은 공고 경력과 총 경력 중 긴 쪽이다. 연한 색은 총 경력, 진한 색은 포지션 관련 경력,
 * 눈금은 공고 경력이다. 총·관련 경력은 적합도 검사 때 외부 AI가 계산한 값을 쓰고,
 * 그 전에는 이력서에 적힌 총 경력(또는 회사별 근무 기간 합)으로 총 경력만 그린다.
 */
@Value
public class CareerGauge {

    private static final Pattern YEARS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:[~～-]\\s*\\d+(?:\\.\\d+)?\\s*)?년");
    private static final Pattern MONTHS = Pattern.compile("(\\d+)\\s*개월");
    private static final Pattern AT_MOST = Pattern.compile("이하|미만|까지");

    int actualMonths;
    /** 공고 경력. 공고에 숫자가 없으면(신입, 경력 무관) null */
    Integer requiredMonths;
    /** 포지션 관련 경력. 적합도 검사 전이면 null */
    Integer relevantMonths;

    public static CareerGauge of(CandidateProfile profile, String requiredExperience, CareerEvalResult eval) {
        Integer required = months(requiredExperience);
        if (required != null && required == 0) {
            required = null;
        }
        if (eval != null && eval.getTotalMonths() != null) {
            return eval.getTotalMonths() == 0 && required == null ? null
                    : new CareerGauge(eval.getTotalMonths(), required, eval.getRelevantMonths());
        }
        if (profile == null || required == null) {
            return null;
        }
        Integer actual = months(profile.getTotalCareer());
        if (actual == null) {
            List<CandidateProfile.Career> careers = profile.getCareers() == null ? List.of() : profile.getCareers();
            List<Integer> durations = careers.stream().map(c -> months(c.getDuration())).toList();
            if (durations.isEmpty() || durations.contains(null)) {
                return null;
            }
            actual = durations.stream().mapToInt(Integer::intValue).sum();
        }
        return new CareerGauge(actual, required, null);
    }

    /** "경력 3년 이하"처럼 공고 경력이 상한인지 */
    public static boolean atMost(String text) {
        return text != null && AT_MOST.matcher(text).find();
    }

    /** 공고 경력 조건을 채우는지. 상한("이하")이면 그보다 짧아야, 아니면 그 이상이어야 충족 */
    static boolean meets(String required, int requiredMonths, int candidateMonths) {
        return atMost(required) ? candidateMonths <= requiredMonths : candidateMonths >= requiredMonths;
    }

    /** "총 2년 3개월", "5년 ↑", "3~5년"(앞 숫자), "1.5년", "18개월" → 개월 수. 숫자가 없으면 null */
    static Integer months(String text) {
        if (text == null) {
            return null;
        }
        Matcher y = YEARS.matcher(text);
        Matcher m = MONTHS.matcher(text);
        boolean hasYears = y.find();
        boolean hasMonths = m.find();
        if (!hasYears && !hasMonths) {
            return null;
        }
        long total = Math.round((hasYears ? Double.parseDouble(y.group(1)) * 12 : 0)
                + (hasMonths ? Integer.parseInt(m.group(1)) : 0));
        return (int) total;
    }

    /** 공고와 비교하는 경력: 관련 경력이 있으면 그것, 없으면 총 경력 */
    private int compared() {
        return relevantMonths != null ? relevantMonths : actualMonths;
    }

    public boolean isOver() {
        return requiredMonths != null && compared() > requiredMonths;
    }

    public int getTotalPercent() {
        return percent(actualMonths);
    }

    public Integer getRelevantPercent() {
        return relevantMonths == null ? null : percent(relevantMonths);
    }

    public Integer getMarkPercent() {
        return requiredMonths == null ? null : percent(requiredMonths);
    }

    /** 공고보다 많은 만큼. 예: "1년 2개월" */
    public String getDiffText() {
        return requiredMonths == null ? "" : format(Math.abs(compared() - requiredMonths));
    }

    public String getActualText() {
        return format(actualMonths);
    }

    public String getRelevantText() {
        return relevantMonths == null ? "" : format(relevantMonths);
    }

    static String format(int months) {
        int y = months / 12;
        int m = months % 12;
        if (y > 0 && m > 0) {
            return y + "년 " + m + "개월";
        }
        return y > 0 ? y + "년" : m + "개월";
    }

    private int percent(int months) {
        int scale = Math.max(actualMonths, requiredMonths == null ? 0 : requiredMonths);
        return scale == 0 ? 0 : (int) Math.round(100.0 * months / scale);
    }
}
