package com.example.interview.web.dto;

import java.util.List;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.EvidenceType;
import com.example.interview.domain.Position;

import lombok.Value;

/**
 * 지원자 비교용 점수 (사용자 규칙). AI가 매기지 않고, 이미 나온 충족 여부와 근거를 코드가 더한다.
 * 평가항목마다: 직접 근거 1, 간접 근거 0.5, 근거 없음 0. 필수·우대 구분은 없다.
 * 경력·학력은 점수에 넣지 않는다 (사용자, 2026-10-22). 충족 1·미충족 0으로만 들고 있어 지원자 비교의 표시와 미충족 제외에 쓴다.
 * 공고에 조건이 없으면 1, 이력서에 정보가 없으면 0. 연봉은 평가하지 않는다.
 */
@Value
public class FitScore {

    int career;
    int education;
    List<Item> items;

    @Value
    public static class Item {
        String name;
        /** 검사 뒤에 추가한 항목이면 null */
        EvidenceType match;

        public double getPoints() {
            return match == null ? 0 : points(match);
        }
    }

    public static FitScore of(Position position, CareerEvalResult eval, CareerGauge gauge,
            List<CriterionEvidenceView> criteria) {
        List<Item> items = criteria.stream()
                .map(c -> new Item(c.getCriterion().getName(), c.isChecked() ? c.getMatch() : null))
                .toList();
        return new FitScore(career(position.getRequiredExperience(), gauge, eval), education(eval), items);
    }

    /** 외부 AI가 지금 공고 조건으로 판단했으면 그 값(판단 못 함은 0), 아니면 코드로 비교 */
    static int career(String required, CareerGauge gauge, CareerEvalResult eval) {
        String verdict = eval == null || required == null || required.isBlank() ? null : eval.careerVerdict(required);
        if (verdict == null) {
            return career(required, gauge);
        }
        return "MET".equals(verdict) ? 1 : 0;
    }

    static int career(String required, CareerGauge gauge) {
        Integer months = CareerGauge.months(required);
        if (months == null || months == 0) {
            return 1;
        }
        if (gauge == null) {
            return 0;
        }
        int candidate = gauge.getRelevantMonths() != null ? gauge.getRelevantMonths() : gauge.getActualMonths();
        return CareerGauge.meets(required, candidate) ? 1 : 0;
    }

    static int education(CareerEvalResult eval) {
        if (eval == null) {
            return 0;
        }
        if (blank(eval.getRequiredEducation()) && blank(eval.getRequiredMajor())) {
            return 1;
        }
        return "MET".equals(eval.getEducationMeets()) ? 1 : 0;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    static double points(EvidenceType match) {
        return switch (match) {
            case DIRECT -> 1;
            case INDIRECT -> 0.5;
            default -> 0;
        };
    }

    public double getTotal() {
        return items.stream().mapToDouble(Item::getPoints).sum();
    }

    public int getMax() {
        return items.size();
    }

    /** 0~100, 막대 폭 */
    public double getPercent() {
        return getMax() == 0 ? 0 : Math.round(getTotal() / getMax() * 1000) / 10.0;
    }

    /** "8.5", "9" */
    public String getTotalText() {
        double t = getTotal();
        return t == Math.floor(t) ? String.valueOf((int) t) : String.valueOf(t);
    }

    /** 모든 평가항목으로 검사했는지. 아니면 다른 지원자와 비교할 수 없다 */
    public boolean isComplete() {
        return items.stream().allMatch(i -> i.getMatch() != null);
    }

    public long getDirectCount() {
        return items.stream().filter(i -> i.getMatch() == EvidenceType.DIRECT).count();
    }
}
