package com.example.interview.web.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.CandidateProfile;

import lombok.Value;

/**
 * 경력·학력 카드의 한 줄: 왼쪽 지원자 값, 가운데 관계 기호, 오른쪽 공고 요구.
 * 예: "2년 3개월 ≥ 2년 이상", "컴퓨터공학 ≠ 전자·전기 계열"
 */
@Value
public class CompareLine {

    private static final Pattern NOT_FINISHED = Pattern.compile("중퇴|재학|휴학|수료|제적");

    String value;
    String valueCaption;
    /** 공고 요구. 조건이 없으면 null */
    String required;
    String requiredCaption;
    /** ≥ < ≤ > = ≠, 비교하지 않으면 null */
    String sign;
    /** 충족 여부. 모르면 null */
    Boolean met;
    /** 미충족일 때 차이. 예: "9개월 부족" */
    String note;

    /** 끝에 붙은 괄호: "학력무관(예정자 가능)". "대졸(4년제) 이상"처럼 괄호 뒤에 글이 있으면 나누지 않는다 */
    private static final Pattern TRAILING_NOTE = Pattern.compile("(.+?)\\s*\\(([^()]+)\\)\\s*");

    /** 공고 요구에서 끝 괄호 앞 본문 ("학력무관"). 괄호 안은 작은 줄로 따로 쓴다 */
    public String getRequiredMain() {
        if (required == null) {
            return null;
        }
        Matcher m = TRAILING_NOTE.matcher(required);
        return m.matches() ? m.group(1).strip() : required;
    }

    /** 공고 요구의 끝 괄호 안 ("예정자 가능"). 없으면 빈 문자열 */
    public String getRequiredNote() {
        if (required == null) {
            return "";
        }
        Matcher m = TRAILING_NOTE.matcher(required);
        return m.matches() ? m.group(2).strip() : "";
    }

    /** 경력: 포지션 관련 경력(없으면 총 경력) vs 공고 경력 */
    public static CompareLine career(CareerGauge gauge, String fallback, String required, Boolean met) {
        Integer months = gauge == null ? null
                : gauge.getRelevantMonths() != null ? gauge.getRelevantMonths() : gauge.getActualMonths();
        String value = months != null ? CareerGauge.format(months) : blank(fallback) ? "-" : fallback;
        String caption = gauge != null && gauge.getRelevantMonths() == null ? "총 경력" : "포지션 관련 경력";
        if (blank(required)) {
            return new CompareLine(value, caption, null, "공고", null, null, null);
        }
        String sign = null;
        String note = null;
        Integer min = CareerGauge.minMonths(required);
        Integer max = CareerGauge.maxMonths(required);
        if (met != null) {
            boolean capOnly = min == null && max != null;
            sign = capOnly ? (met ? "≤" : ">") : (met ? "≥" : "<");
            if (!met && months != null) {
                if (min != null && months < min) {
                    note = CareerGauge.format(min - months) + " 부족";
                } else if (max != null && months > max) {
                    note = CareerGauge.format(months - max) + " 초과";
                }
            }
        }
        return new CompareLine(value, caption, required, "공고", sign, met, note);
    }

    /**
     * 학력: 최종 학력 줄과, 공고에 학과 조건이 있으면 전공 줄. EDUCATION_EVAL의 충족 여부는 둘을 합친 것이라
     * 학력 줄은 고졸~박사 단계로 나눠 보고, 전공 줄은 학력 줄이 맞는데 전체가 미충족일 때만 ≠로 본다.
     */
    public static List<CompareLine> education(CareerEvalResult eval, CandidateProfile profile,
            String requiredEducation, String requiredMajor, Boolean met) {
        String finalEducation = eval == null ? null : eval.getFinalEducation();
        List<CompareLine> lines = new ArrayList<>();
        Boolean degreeMet = null;
        if (blank(requiredEducation) || requiredEducation.contains("무관")) {
            degreeMet = blank(requiredEducation) ? null : Boolean.TRUE;
        } else {
            Integer have = level(finalEducation);
            if (have != null && NOT_FINISHED.matcher(finalEducation).find()) {
                have--;
            }
            Integer need = level(requiredEducation);
            degreeMet = have != null && need != null ? Boolean.valueOf(have >= need)
                    : blank(requiredMajor) ? met : null;
        }
        lines.add(new CompareLine(blank(finalEducation) ? "-" : finalEducation, "최종 학력",
                blank(requiredEducation) ? null : requiredEducation, "공고",
                degreeMet == null || blank(requiredEducation) || requiredEducation.contains("무관") ? null
                        : degreeMet ? "≥" : "<",
                degreeMet, null));
        if (!blank(requiredMajor)) {
            Boolean majorMet = met == null ? null
                    : met ? Boolean.TRUE
                    : Boolean.TRUE.equals(degreeMet) || blank(requiredEducation) ? Boolean.FALSE : null;
            String major = major(profile, finalEducation);
            lines.add(new CompareLine(blank(major) ? "-" : major, "전공", requiredMajor, "공고 학과",
                    majorMet == null ? null : majorMet ? "=" : "≠", majorMet, null));
        }
        return lines;
    }

    /** 최종 학력 글에 이름이 나오는 학교의 전공, 없으면 마지막으로 적힌 전공 */
    static String major(CandidateProfile profile, String finalEducation) {
        if (profile == null || profile.getEducation() == null) {
            return null;
        }
        String last = null;
        for (CandidateProfile.Education e : profile.getEducation()) {
            if (blank(e.getMajor())) {
                continue;
            }
            if (!blank(finalEducation) && !blank(e.getSchool()) && finalEducation.contains(e.getSchool())) {
                return e.getMajor();
            }
            last = e.getMajor();
        }
        return last;
    }

    /** 학력 글 → 1(고졸)~5(박사). 읽을 수 없으면 null */
    static Integer level(String text) {
        if (blank(text)) {
            return null;
        }
        String t = text.replace(" ", "");
        if (t.contains("박사")) {
            return 5;
        }
        if (t.contains("석사") || t.contains("대학원")) {
            return 4;
        }
        if (t.contains("초대졸") || t.contains("전문") || t.contains("2·3년") || t.contains("2,3년")
                || t.contains("2년제") || t.contains("3년제")) {
            return 2;
        }
        if (t.contains("대졸") || t.contains("학사") || t.contains("4년") || t.contains("대학")) {
            return 3;
        }
        if (t.contains("고졸") || t.contains("고등")) {
            return 1;
        }
        return null;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
