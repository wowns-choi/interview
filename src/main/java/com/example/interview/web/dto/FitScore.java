package com.example.interview.web.dto;

import java.util.List;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.EvidenceType;

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

    public static FitScore of(CareerEvalResult eval, List<CriterionEvidenceView> criteria) {
        List<Item> items = criteria.stream()
                .map(c -> new Item(c.getCriterion().getName(), c.isChecked() ? c.getMatch() : null))
                .toList();
        return new FitScore(career(eval), education(eval), items);
    }

    /** 검사 때 정한 경력 판정: 충족·조건 없음(UNKNOWN) 1, 미충족이나 경력을 못 셈 0 */
    static int career(CareerEvalResult eval) {
        if (eval == null || eval.getCareerMeets() == null) {
            return 0;
        }
        return "NOT_MET".equals(eval.getCareerMeets()) ? 0 : 1;
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

    /** 100점 만점 정수 (사용자 2026-10-28: 지원자마다 검사한 기준 수가 달라도 비교할 수 있게). 7개 중 5.5 → 79 */
    public long getScore100() {
        return getMax() == 0 ? 0 : Math.round(getTotal() / getMax() * 100);
    }

    /** 순위용: 반올림하지 않은 비율 */
    public double getRatio() {
        return getMax() == 0 ? 0 : getTotal() / getMax();
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
