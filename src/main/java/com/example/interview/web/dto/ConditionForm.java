package com.example.interview.web.dto;

import java.util.regex.Pattern;

import com.example.interview.domain.Position;

import lombok.Value;

/**
 * 경력·학력 조건 고치기 드롭다운의 처음 선택값. 공고 표기(AI ①이 옮긴 글)를 읽어 고른다.
 * 저장할 때는 화면이 정해진 문장("경력 3년 이상", "대졸(4년제) 이상")으로 만들어 보낸다.
 */
@Value
public class ConditionForm {

    private static final Pattern ANY = Pattern.compile("무관|신입");

    /** "", ANY(경력무관), MIN(N년 이상), MAX(N년 이하) */
    String expKind;
    Integer expYears;
    /** "", ANY, HIGH, COLLEGE, UNIV, MASTER, DOCTOR */
    String eduLevel;

    public static ConditionForm of(Position p) {
        String exp = p.getRequiredExperience();
        String expKind = "";
        Integer years = null;
        if (exp != null && !exp.isBlank()) {
            Integer months = CareerGauge.months(exp);
            if (months != null && months > 0) {
                years = Math.max(1, Math.round(months / 12f));
                expKind = CareerGauge.atMost(exp) ? "MAX" : "MIN";
            } else if (ANY.matcher(exp).find()) {
                expKind = "ANY";
            }
        }
        return new ConditionForm(expKind, years, education(p.getRequiredEducation()));
    }

    static String education(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String t = text.replace(" ", "");
        if (t.contains("무관")) {
            return "ANY";
        }
        if (t.contains("박사")) {
            return "DOCTOR";
        }
        if (t.contains("석사") || t.contains("대학원")) {
            return "MASTER";
        }
        if (t.contains("초대졸") || t.contains("전문대") || t.contains("2년") || t.contains("3년")) {
            return "COLLEGE";
        }
        if (t.contains("대졸") || t.contains("4년") || t.contains("학사") || t.contains("대학교")) {
            return "UNIV";
        }
        if (t.contains("고졸") || t.contains("고등")) {
            return "HIGH";
        }
        return "";
    }
}
