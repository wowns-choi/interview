package com.example.interview.web.dto;

import com.example.interview.ai.dto.CareerEvalResult;

import lombok.Value;

/**
 * 공고 조건 충족 도장. true = 충족, false = 미충족, null = 판단할 수 없음(도장 없음).
 * 학력은 적합도 검사 때 외부 AI가 판단한 값, 경력은 요구 경력 개월과 관련 경력 개월을 코드가 비교한 값 (사용자 2026-11-09).
 */
@Value
public class RequirementFit {

    Boolean career;
    Boolean education;

    public static RequirementFit of(CareerEvalResult eval) {
        return new RequirementFit(career(eval), education(eval));
    }

    /** 적합도 검사 때 코드가 정한 경력 판정 (검사 당시 요구 경력 개월과 관련 경력 개월 비교). 조건 없음·판정 없음은 null */
    static Boolean career(CareerEvalResult eval) {
        return verdict(eval == null ? null : eval.getCareerMeets());
    }

    static Boolean education(CareerEvalResult eval) {
        return verdict(eval == null ? null : eval.getEducationMeets());
    }

    private static Boolean verdict(String meets) {
        if (meets == null) {
            return null;
        }
        return switch (meets) {
            case "MET" -> Boolean.TRUE;
            case "NOT_MET" -> Boolean.FALSE;
            default -> null;
        };
    }
}
