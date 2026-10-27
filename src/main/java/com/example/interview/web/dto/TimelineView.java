package com.example.interview.web.dto;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.CandidateProfile;

import lombok.Value;

/**
 * 이력 타임라인. 외부 AI가 YYYY-MM으로 읽은 기간을 이름표(A사, 학교1, 교육1, 활동1)로 화면의 실제 이름에 다시 붙인다.
 * 첫 기록부터 기준 월까지 어떤 기록에도 덮이지 않은 3개월 이상을 빈 기간으로 표시한다.
 * 위치는 모두 전체 기간에 대한 백분율이다. 글자가 막대 안에 다 들어가도록 시간 축의 최소 폭(px)을 정하고(화면은 가로 스크롤),
 * 그래도 좁은 막대는 이름을 막대 옆에 쓴다. 겹침은 이름이 차지하는 자리까지 넣어 줄을 나눈다.
 */
@Value
public class TimelineView {

    static final int MIN_GAP_MONTHS = 3;
    /** 이보다 넓혀야 들어가는 막대는 이름을 옆에 쓴다 */
    static final int MAX_WIDTH = 8000;
    static final int MIN_WIDTH = 900;
    private static final double OUTSIDE_GAP = 8;

    List<Lane> lanes;
    List<Gap> gaps;
    List<Tick> ticks;
    /** 시간 축 최소 폭(px) */
    int width;

    @Value
    public static class Lane {
        String name;
        String tone;
        /** 겹치는 항목은 아래 줄로 내린다 */
        List<List<Bar>> rows;
    }

    /** PDF 목록의 한 줄 */
    @Value
    public static class Entry {
        String kind;
        String tone;
        String label;
        String detail;
        String periodText;
        double left;
    }

    /** PDF 목록용: 구분과 상관없이 시작 순서로 (같으면 경력·학력·교육·활동·기록 없음 순) */
    public List<Entry> getEntries() {
        List<Entry> entries = new ArrayList<>();
        for (Lane lane : lanes) {
            lane.getRows().stream().flatMap(List::stream).forEach(b -> entries.add(
                    new Entry(lane.getName(), lane.getTone(), b.getLabel(), b.getDetail(), b.getPeriodText(), b.getLeft())));
        }
        gaps.forEach(g -> entries.add(new Entry("기록 없음", "gap", g.getLengthText(), "", g.getPeriodText(), g.getLeft())));
        entries.sort(Comparator.comparingDouble(Entry::getLeft));
        return entries;
    }

    @Value
    public static class Bar {
        String label;
        String detail;
        String periodText;
        double left;
        double width;
        /** 이름을 막대 밖에 쓰면 "right" 또는 "left", 안이면 null */
        String side;
    }

    @Value
    public static class Gap {
        String periodText;
        String lengthText;
        double left;
        double width;
    }

    @Value
    public static class Tick {
        int year;
        double left;
    }

    private record Item(String lane, String label, String detail, int start, int end) {
    }

    private record LaneSpec(String name, String tone) {
    }

    private static final List<LaneSpec> LANES = List.of(new LaneSpec("경력", "career"), new LaneSpec("학력", "school"),
            new LaneSpec("교육", "training"), new LaneSpec("활동", "activity"));

    /** 읽은 기간이 하나도 없으면 null */
    public static TimelineView of(CandidateProfile profile, CareerEvalResult eval, YearMonth now) {
        if (profile == null || eval == null || eval.getTimeline() == null) {
            return null;
        }
        Map<String, String[]> names = names(profile, eval);
        List<Item> items = new ArrayList<>();
        for (CareerEvalResult.Period p : eval.getTimeline()) {
            String[] n = names.get(p.getRef() == null ? "" : p.getRef().strip());
            if (n == null) {
                continue;
            }
            int start = index(p.getStart());
            int end = index(p.getEnd());
            if (start > end) {
                continue;
            }
            items.add(new Item(n[0], n[1], n[2], start, end));
        }
        if (items.isEmpty()) {
            return null;
        }
        int nowIndex = index(now);
        int from = items.stream().mapToInt(Item::start).min().getAsInt();
        int to = Math.max(nowIndex, items.stream().mapToInt(Item::end).max().getAsInt());
        double span = to - from + 1;

        boolean[] covered = new boolean[to - from + 1];
        items.forEach(i -> {
            for (int m = i.start(); m <= i.end(); m++) {
                covered[m - from] = true;
            }
        });
        List<Gap> gaps = new ArrayList<>();
        int m = 0;
        while (m <= nowIndex - from) {
            if (covered[m]) {
                m++;
                continue;
            }
            int s = m;
            while (m <= nowIndex - from && !covered[m]) {
                m++;
            }
            int length = m - s;
            if (length >= MIN_GAP_MONTHS) {
                gaps.add(new Gap(period(from + s, from + m - 1), CareerGauge.format(length),
                        pct(s / span), pct(length / span)));
            }
        }

        // 막대 안에 글자가 들어가는 폭 중 가장 큰 것 (MAX_WIDTH를 넘는 막대는 빼고 이름을 옆에 쓴다)
        double width = MIN_WIDTH;
        for (Item i : items) {
            width = fitWidth(width, need(i.label(), period(i.start(), i.end())), (i.end() - i.start() + 1) / span);
        }
        for (Gap g : gaps) {
            width = fitWidth(width, need(g.getLengthText(), g.getPeriodText()), g.getWidth() / 100);
        }

        List<Lane> lanes = new ArrayList<>();
        for (LaneSpec spec : LANES) {
            List<Item> inLane = items.stream().filter(i -> i.lane().equals(spec.name()))
                    .sorted((a, b) -> Integer.compare(a.start(), b.start())).toList();
            List<List<Bar>> rows = new ArrayList<>();
            List<Double> rowEnds = new ArrayList<>();
            for (Item i : inLane) {
                String periodText = period(i.start(), i.end());
                double left = pct((i.start() - from) / span);
                double barWidth = pct((i.end() - i.start() + 1) / span);
                double labelPct = (need(i.label(), periodText) + OUTSIDE_GAP) / width * 100;
                String side = null;
                double extentFrom = left;
                double extentTo = left + barWidth;
                if (need(i.label(), periodText) > (i.end() - i.start() + 1) / span * width + 0.5) {
                    side = left + barWidth + labelPct <= 100 ? "right" : "left";
                    if (side.equals("right")) {
                        extentTo += labelPct;
                    } else {
                        extentFrom -= labelPct;
                    }
                }
                int row = 0;
                while (row < rowEnds.size() && rowEnds.get(row) > extentFrom + 1e-6) {
                    row++;
                }
                if (row == rowEnds.size()) {
                    rows.add(new ArrayList<>());
                    rowEnds.add(extentTo);
                } else {
                    rowEnds.set(row, extentTo);
                }
                rows.get(row).add(new Bar(i.label(), i.detail(), periodText, left, barWidth, side));
            }
            lanes.add(new Lane(spec.name(), spec.tone(), rows));
        }

        List<Tick> ticks = new ArrayList<>();
        int firstYear = from / 12 + 1;
        int lastYear = to / 12;
        int step = Math.max(1, (int) Math.ceil((lastYear - firstYear + 1) / 10.0));
        for (int y = firstYear; y <= lastYear; y += step) {
            ticks.add(new Tick(y, pct((y * 12 - from) / span)));
        }
        return new TimelineView(lanes, gaps, ticks, (int) Math.ceil(width));
    }

    private static double fitWidth(double width, double needPx, double fraction) {
        double fit = needPx / fraction;
        return fit <= MAX_WIDTH ? Math.max(width, fit) : width;
    }

    /** 막대에 이름(굵게 14.5px)과 기간(12.5px)을 두 줄로 쓰는 데 드는 폭(px). 안쪽 여백과 왼쪽 색 띠 포함 */
    static double need(String label, String period) {
        return Math.max(textWidth(label, 14.5), textWidth(period, 12.5)) * 1.08 + 28;
    }

    private static double textWidth(String s, double font) {
        double w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += c >= 0x2E80 ? font : c == ' ' ? font * 0.3 : font * 0.6;
        }
        return w;
    }

    /** 업로드 때 저장한 별칭 → {줄, 화면 이름, 덧붙일 설명}. 경력은 외부 AI가 센 회사만 */
    private static Map<String, String[]> names(CandidateProfile p, CareerEvalResult eval) {
        Map<String, String[]> names = new HashMap<>();
        for (CandidateProfile.Career c : p.getCareers()) {
            if (!eval.counts(c.getAlias())) {
                continue; // 외부 AI가 경력으로 세지 않은 것 (고객사 등)
            }
            names.put(c.getAlias(), new String[] {"경력", or(c.getCompany(), "회사"),
                    eval.unrelated(c.getAlias()) ? join(or(c.getDepartment(), ""), "포지션 무관") : c.getDepartment()});
        }
        for (CandidateProfile.Education e : p.getEducation()) {
            if (e.isLevelOnly()) {
                continue; // 「최종학력: 대학교(4년) 졸업」 같은 요약 줄
            }
            names.put(e.getAlias(), new String[] {"학력", or(e.getSchool(), "학교"), join(e.getMajor(), e.getStatus())});
        }
        for (CandidateProfile.Training t : p.getTrainings()) {
            names.put(t.getAlias(), new String[] {"교육",
                    or(t.getName(), or(t.getInstitution(), "교육")), t.getInstitution().equals(t.getName()) ? "" : t.getInstitution()});
        }
        for (CandidateProfile.Activity a : p.getActivities()) {
            names.put(a.getAlias(), new String[] {"활동", or(a.getName(), or(a.getKind(), "활동")),
                    a.getName().isBlank() ? "" : a.getKind()});
        }
        return names;
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String join(String a, String b) {
        return a.isBlank() ? b : b.isBlank() ? a : a + " · " + b;
    }

    private static int index(String yearMonth) {
        return index(YearMonth.parse(yearMonth));
    }

    private static int index(YearMonth ym) {
        return ym.getYear() * 12 + ym.getMonthValue() - 1;
    }

    private static String period(int start, int end) {
        return ym(start) + " ~ " + ym(end);
    }

    private static String ym(int index) {
        return String.format("%d.%02d", index / 12, index % 12 + 1);
    }

    private static double pct(double ratio) {
        return Math.round(ratio * 10000) / 100.0;
    }
}
