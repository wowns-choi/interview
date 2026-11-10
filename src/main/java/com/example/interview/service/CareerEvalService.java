package com.example.interview.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.interview.ai.AiClient;
import com.example.interview.ai.PromptLoader;
import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.ai.dto.EducationResult;
import com.example.interview.ai.dto.EvidenceResult;
import com.example.interview.ai.dto.TimelineResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.CareerRange;
import com.example.interview.domain.CandidateProfile;
import com.example.interview.domain.Position;
import com.example.interview.mapper.CandidateMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 적합도 검사 때 학력(EDUCATION_EVAL)과 타임라인(TIMELINE)을 외부 AI로 따로 묻고,
 * 근거 분석 호출에서 받은 총 경력·관련 경력과 합쳐 저장한다. 두 호출 모두 이력서 글을 보내지 않는다.
 * 학력 호출은 학교 이름·상태·기간과 포지션의 학력 조건만, 타임라인 호출은 이름표("A사", "학교1", "교육1", "활동1")와 기간만 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareerEvalService {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    /** 타임라인 항목 이름표: "학교1", "교육1", "활동1" (회사는 alias) */
    public static final String SCHOOL = "학교";
    public static final String TRAINING = "교육";
    public static final String ACTIVITY = "활동";

    private final AiClient aiClient;
    private final PromptLoader promptLoader;
    private final PositionService positionService;
    private final CandidateMapper candidateMapper;

    /** 최종 학력과 학력 조건 충족 여부. 학교가 없으면 묻지 않는다. 실패하면 null */
    public CareerEvalResult education(Candidate candidate) {
        CandidateProfile profile = ReportService.parseProfile(candidate.getProfileJson());
        Position position = positionService.get(candidate.getPositionId());
        String required = position.getRequiredEducation() == null ? "" : position.getRequiredEducation().strip();
        String major = position.getRequiredMajor() == null ? "" : position.getRequiredMajor().strip();
        CareerEvalResult r = new CareerEvalResult();
        r.setRequiredEducation(required);
        r.setRequiredMajor(major);
        if (profile == null || profile.getEducation().isEmpty()) {
            r.setFinalEducation("");
            r.setEducationReason("");
            r.setEducationMeets("UNKNOWN");
            return r;
        }
        try {
            EducationResult e = aiClient.call(AiPurpose.EDUCATION_EVAL, candidate.getId(), promptLoader.load("education_eval"),
                    educationContent(required, major, profile), EducationResult.class, EducationResult::validate);
            r.setFinalEducation(e.getFinalEducation());
            r.setEducationReason(e.getEducationReason());
            // 조건이 없으면 AI 답과 상관없이 판단하지 않는다
            r.setEducationMeets(required.isEmpty() && major.isEmpty() ? "UNKNOWN" : e.getEducationMeets());
            return r;
        } catch (RuntimeException e) {
            log.warn("학력 판단 실패 candidateId={}: {}", candidate.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 경력 조건 충족 여부를 코드로 정한다 (사용자 2026-11-09, GPT를 부르지 않는다): 포지션의 요구 경력 개월(하한·상한)과
     * 근거 분석이 센 관련 경력 개월(없으면 총 경력)을 비교한다. 판단에 쓴 조건도 함께 남겨, 조건을 고쳐도 판정은 그대로 둔다.
     * 조건이 없으면 UNKNOWN. 경력 개월을 못 셌으면 null
     */
    public CareerEvalResult judgeCareer(Candidate candidate, Integer relevantMonths, Integer totalMonths) {
        Position position = positionService.get(candidate.getPositionId());
        String required = position.getRequiredExperience() == null ? "" : position.getRequiredExperience().strip();
        CareerEvalResult r = new CareerEvalResult();
        r.setCareerRequirement(required);
        if (required.isEmpty()) {
            r.setCareerMeets("UNKNOWN");
            r.setCareerReason("");
            return r;
        }
        Integer min = position.getCareerMinMonths();
        Integer max = position.getCareerMaxMonths();
        if (min == null && max == null && required.chars().anyMatch(Character::isDigit)) {
            // 개월이 없는데 문구에 숫자가 있다 = 개월을 저장하기 전 포지션 (사용자 2026-11-10: "경력 10년 이상"이 경력무관으로 충족됐다)
            log.warn("요구 경력 개월이 없어 경력을 판단하지 않음 positionId={} required={}", position.getId(), required);
            return null;
        }
        r.setCareerMinMonths(min);
        r.setCareerMaxMonths(max);
        Integer months = relevantMonths != null ? relevantMonths : totalMonths;
        Boolean met = CareerRange.meets(min, max, months);
        if (met == null) {
            return null;
        }
        r.setCareerMeets(met ? "MET" : "NOT_MET");
        r.setCareerReason(min == null && max == null ? "경력무관"
                : (relevantMonths != null ? "포지션 관련 경력 " : "총 경력 ") + months + "개월, 공고 " + CareerRange.describe(min, max));
        return r;
    }

    /**
     * 타임라인 기간 (사용자 2026-11-10): 근거 분석(GPT)이 가린 이력서의 이름표 옆에서 읽은 기간을 쓰고, 그 답에 없는 항목만
     * 업로드 때 로컬 AI가 옮겨 둔 기간 글을 코드로 읽는다 (병역처럼 가려서 GPT가 볼 수 없는 기간, 근거 분석이 빠뜨린 항목).
     * 이 프로필에 없는 이름표는 버린다. 따로 AI를 부르지 않는다.
     *
     * @param fromEvidence 근거 분석의 timeline. null이면 근거 분석이 실패한 것이라 null을 돌려 이전 값을 둔다
     */
    public static List<CareerEvalResult.Period> timeline(CandidateProfile profile, List<CareerEvalResult.Period> fromEvidence,
            YearMonth now) {
        if (fromEvidence == null) {
            return null;
        }
        Map<String, String> localPeriods = new LinkedHashMap<>();
        if (profile != null) {
            profile.getCareers().forEach(c -> localPeriods.put(c.getAlias(), c.getPeriod()));
            profile.getEducation().forEach(e -> localPeriods.put(e.getAlias(), e.getPeriod()));
            profile.getTrainings().forEach(t -> localPeriods.put(t.getAlias(), t.getPeriod()));
            profile.getActivities().forEach(a -> localPeriods.put(a.getAlias(), a.getPeriod()));
        }
        List<CareerEvalResult.Period> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CareerEvalResult.Period p : fromEvidence) {
            String ref = p.getRef().replace("[", "").replace("]", "").strip();
            if (localPeriods.containsKey(ref) && seen.add(ref)) {
                p.setRef(ref);
                out.add(p);
            }
        }
        localPeriods.forEach((ref, period) -> {
            if (!seen.contains(ref)) {
                CareerEvalResult.Period p = parsePeriod(ref, period, now);
                if (p != null) {
                    out.add(p);
                }
            }
        });
        return out;
    }

    private static final Pattern YEAR_MONTH = Pattern.compile("((?:19|20)\\d{2})\\s*(?:[./-]|년)\\s*(\\d{1,2})(?!\\d)");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern ONGOING = Pattern.compile("재직|재학|현재|진행|중$|~\\s*$");

    /** "2024.08 ~ 2026.06", "2026.06 ~ 재직중", "2015년 3월 ~ 2016년 12월", "2019 ~ 2020"을 연월로. 읽지 못하면 null */
    static CareerEvalResult.Period parsePeriod(String ref, String text, YearMonth now) {
        if (ref == null || ref.isBlank() || text == null || text.isBlank()) {
            return null;
        }
        List<String> months = new ArrayList<>();
        Matcher m = YEAR_MONTH.matcher(text);
        while (m.find()) {
            int month = Integer.parseInt(m.group(2));
            if (month >= 1 && month <= 12) {
                months.add(m.group(1) + "-" + String.format("%02d", month));
            }
        }
        String start;
        String end;
        if (!months.isEmpty()) {
            start = months.get(0);
            end = months.size() > 1 ? months.get(1) : ONGOING.matcher(text.strip()).find() ? now.toString() : start;
        } else {
            List<String> years = YEAR.matcher(text).results().map(r -> r.group(1)).toList();
            if (years.isEmpty()) {
                return null;
            }
            start = years.get(0) + "-01";
            end = years.size() > 1 ? years.get(1) + "-12"
                    : ONGOING.matcher(text.strip()).find() ? now.toString() : years.get(0) + "-12";
        }
        CareerEvalResult.Period p = new CareerEvalResult.Period();
        p.setRef(ref);
        p.setStart(start);
        p.setEnd(end);
        List<CareerEvalResult.Period> valid = TimelineResult.clean(List.of(p));
        return valid.isEmpty() ? null : valid.get(0);
    }

    /**
     * 세 호출의 결과를 합쳐 저장한다. 실패한(null) 쪽은 이전에 저장한 값을 그대로 둔다.
     *
     * @param education 학력 호출 결과 (학력 필드만 씀)
     * @param timeline  타임라인 호출 결과
     * @param career    근거 분석 호출의 경력 계산
     * @param careerMeets 경력 판정 (경력 필드만 씀)
     */
    public void save(Candidate candidate, CareerEvalResult education, List<CareerEvalResult.Period> timeline,
            EvidenceResult.Career career, CareerEvalResult careerMeets) {
        if (education == null && timeline == null && career == null && careerMeets == null) {
            return;
        }
        CareerEvalResult previous = parse(candidate.getCareerEvalJson());
        CareerEvalResult r = previous != null ? previous : new CareerEvalResult();
        if (education != null) {
            r.setFinalEducation(education.getFinalEducation());
            r.setEducationReason(education.getEducationReason());
            r.setRequiredEducation(education.getRequiredEducation());
            r.setRequiredMajor(education.getRequiredMajor());
            r.setEducationMeets(education.getEducationMeets());
        }
        if (timeline != null) {
            r.setTimeline(timeline);
        }
        if (career != null) {
            // 자격증도 근거 분석 답을 쓴다. 「로컬 AI로 처리」와 상관없이 검사 결과는 같다 (사용자 2026-11-10)
            r.applyCareer(career);
        }
        if (careerMeets != null) {
            r.setCareerMeets(careerMeets.getCareerMeets());
            r.setCareerReason(careerMeets.getCareerReason());
            r.setCareerRequirement(careerMeets.getCareerRequirement());
            r.setCareerMinMonths(careerMeets.getCareerMinMonths());
            r.setCareerMaxMonths(careerMeets.getCareerMaxMonths());
        }
        candidateMapper.updateCareerEval(candidate.getId(), JSON.writeValueAsString(r));
    }

    /** "학교명 | 상태 | 기간". 요구 학과가 있을 때만 전공도 보낸다: "학교명 | 전공 | 상태 | 기간" */
    static String educationContent(String required, String major, CandidateProfile profile) {
        StringBuilder sb = new StringBuilder();
        if (!required.isEmpty()) {
            sb.append("<education_requirement>").append(required).append("</education_requirement>\n\n");
        }
        if (!major.isEmpty()) {
            sb.append("<major_requirement>").append(major).append("</major_requirement>\n\n");
        }
        sb.append("<schools>\n");
        for (CandidateProfile.Education e : profile.getEducation()) {
            sb.append(e.getSchool()).append(" | ");
            if (!major.isEmpty()) {
                sb.append(e.getMajor()).append(" | ");
            }
            sb.append(e.getStatus()).append(" | ").append(e.getPeriod()).append("\n");
        }
        sb.append("</schools>");
        if (!profile.getEducationNotes().isEmpty()) {
            sb.append("\n\n<education_summary>\n");
            profile.getEducationNotes().forEach(n -> sb.append(n).append("\n"));
            sb.append("</education_summary>");
        }
        return sb.toString();
    }

    /** 0 → "A사", 25 → "Z사", 26 → "회사27" */
    public static String alias(int i) {
        return i < 26 ? (char) ('A' + i) + "사" : "회사" + (i + 1);
    }

    static CareerEvalResult parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, CareerEvalResult.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
