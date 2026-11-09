package com.example.interview.web.dto;

import com.example.interview.domain.Position;

import lombok.Value;

/**
 * 경력·학력 조건 고치기 드롭다운의 처음 선택값. 경력은 저장된 개월(하한·상한)로, 학력은 공고 표기를 읽어 고른다.
 * 저장할 때는 화면이 정해진 문장("경력 3년 이상", "대졸(4년제) 이상")으로 만들어 보낸다.
 */
@Value
public class ConditionForm {

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
            Integer min = p.getCareerMinMonths();
            Integer max = p.getCareerMaxMonths();
            // 드롭다운은 이상·이하 하나뿐이라 범위("2~5년")는 하한으로 보인다. 하한이 0이면(신입 가능) 상한으로
            if (max != null && (min == null || min == 0)) {
                expKind = "MAX";
                years = Math.max(1, Math.round(max / 12f));
            } else if (min != null && min > 0) {
                expKind = "MIN";
                years = Math.max(1, Math.round(min / 12f));
            } else {
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
