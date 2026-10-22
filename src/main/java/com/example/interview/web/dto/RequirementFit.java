package com.example.interview.web.dto;

import java.util.regex.Pattern;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.Position;

import lombok.Value;

/**
 * 공고 조건 충족 도장. true = 충족, false = 미충족, null = 판단할 수 없음(도장 없음).
 * 경력은 코드로 비교하고, 학력은 적합도 검사 때 외부 AI가 판단한 값을 쓴다.
 */
@Value
public class RequirementFit {

    private static final Pattern NO_REQUIREMENT = Pattern.compile("무관|신입");

    Boolean career;
    Boolean education;

    public static RequirementFit of(Position position, CareerEvalResult eval, CareerGauge gauge) {
        return new RequirementFit(career(position.getRequiredExperience(), gauge), education(eval));
    }

    static Boolean career(String required, CareerGauge gauge) {
        if (required == null || required.isBlank()) {
            return null;
        }
        Integer months = CareerGauge.months(required);
        if (months == null || months == 0) {
            return NO_REQUIREMENT.matcher(required).find() ? Boolean.TRUE : null;
        }
        if (gauge == null) {
            return null;
        }
        int candidate = gauge.getRelevantMonths() != null ? gauge.getRelevantMonths() : gauge.getActualMonths();
        return CareerGauge.meets(required, candidate);
    }

    static Boolean education(CareerEvalResult eval) {
        if (eval == null || eval.getEducationMeets() == null) {
            return null;
        }
        return switch (eval.getEducationMeets()) {
            case "MET" -> Boolean.TRUE;
            case "NOT_MET" -> Boolean.FALSE;
            default -> null;
        };
    }
}
