package com.example.interview.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateStatus;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.Position;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.mapper.EvidenceMapper;
import com.example.interview.web.dto.CriterionEvidenceView;
import com.example.interview.web.dto.ExtractionSummary;
import com.example.interview.web.dto.FitScore;
import com.example.interview.web.dto.RankingRow;

import lombok.RequiredArgsConstructor;

/**
 * 포지션 하나의 지원자를 점수 순으로 줄 세운다.
 * 포지션에서 켜 둔 경우 경력·학력 미충족 지원자는 순위 없이 "제외됨"으로 뺀다.
 * 점수는 지원자마다 검사한 때의 기준으로 매긴 100점 만점이다 (사용자 2026-10-28). 동점이면 직접 근거가 많은 쪽, 그다음 포지션 관련 경력이 긴 쪽이 앞선다.
 * 검사 뒤에 평가항목이 바뀐 지원자(재검사 필요)와 검사 전 지원자는 순위 없이 뒤에 둔다.
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    private static final Comparator<RankingRow> BY_SCORE = Comparator
            .comparingDouble((RankingRow r) -> r.getScore().getRatio()).reversed()
            .thenComparing(Comparator.comparingLong((RankingRow r) -> r.getScore().getDirectCount()).reversed())
            .thenComparing(Comparator.comparing(RankingRow::getRelevantMonths,
                    Comparator.nullsLast(Comparator.<Integer>reverseOrder())))
            .thenComparing(Comparator.comparing(RankingRow::getCandidateId).reversed());

    private final PositionService positionService;
    private final CandidateService candidateService;
    private final CandidateMapper candidateMapper;
    private final EvidenceMapper evidenceMapper;

    public List<RankingRow> rank(Long positionId) {
        Position position = positionService.get(positionId);
        List<EvalCriterion> criteria = positionService.allCriteria(positionId);
        List<RankingRow> ranked = new ArrayList<>();
        List<RankingRow> excluded = new ArrayList<>();
        List<RankingRow> stale = new ArrayList<>();
        List<RankingRow> unchecked = new ArrayList<>();
        for (ExtractionSummary s : candidateMapper.findByPositionId(positionId)) {
            Candidate c = candidateService.get(s.getCandidateId());
            List<Evidence> evidences = evidenceMapper.findByCandidateId(c.getId());
            List<CriterionEvidenceView> views = ReportService.evidenceByCriterion(
                    ReportService.checkedCriteria(c, criteria, evidences), evidences);
            FitScore score = null;
            Integer relevant = null;
            CareerEvalResult eval = CareerEvalService.parse(c.getCareerEvalJson());
            CareerEvalResult.Certificates certs = eval == null ? null : eval.getCertificates();
            // 다시 검사 중(대기·검사 중)이면 예전 점수로 줄 세우지 않고 「검사 전」에 둔다 (사용자 2026-10-28)
            boolean busy = s.getStatus() == CandidateStatus.QUEUED || s.getStatus() == CandidateStatus.MASKING
                    || s.getStatus() == CandidateStatus.ANALYZING;
            if (!busy && views.stream().anyMatch(CriterionEvidenceView::isChecked)) {
                score = FitScore.of(eval, views);
                relevant = eval == null ? null : eval.getRelevantMonths();
            }
            RankingRow row = new RankingRow(s.getCandidateId(), s.getDisplayName(), s.getStatus(), s.getCreatedAt(),
                    score, null, exclusion(position, score), relevant,
                    s.isInterviewed(), s.getInterviewDate(), s.getDecision(),
                    certs != null && Boolean.TRUE.equals(certs.getInfoEngineer()),
                    certs != null && Boolean.TRUE.equals(certs.getSqld()));
            switch (row.getState()) {
                case "RANKED" -> ranked.add(row);
                case "EXCLUDED" -> excluded.add(row);
                case "STALE" -> stale.add(row);
                default -> unchecked.add(row);
            }
        }
        ranked.sort(BY_SCORE);
        excluded.sort(BY_SCORE);
        stale.sort(BY_SCORE);
        List<RankingRow> out = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            RankingRow r = ranked.get(i);
            out.add(new RankingRow(r.getCandidateId(), r.getDisplayName(), r.getStatus(), r.getCreatedAt(),
                    r.getScore(), i + 1, null, r.getRelevantMonths(), r.isInterviewed(), r.getInterviewDate(), r.getDecision(),
                    r.isInfoEngineer(), r.isSqld()));
        }
        out.addAll(excluded);
        out.addAll(stale);
        out.addAll(unchecked);
        return out;
    }

    static String exclusion(Position position, FitScore score) {
        if (score == null) {
            return null;
        }
        boolean career = position.isExcludeCareer() && score.getCareer() == 0;
        boolean education = position.isExcludeEducation() && score.getEducation() == 0;
        if (career && education) {
            return "경력·학력 미충족";
        }
        return career ? "경력 미충족" : education ? "학력 미충족" : null;
    }

    /** 검사한 뒤 바뀌어 다시 검사할 것 */
    public enum Change { CRITERIA, CAREER, EDUCATION }

    /** 다시 검사할 지원자 id (올린 순서) */
    public List<Long> changedCandidates(Long positionId) {
        return new ArrayList<>(changes(positionId).keySet());
    }

    /**
     * 검사한 때의 평가 기준이나 경력·학력 조건이 지금과 다른 지원자와 무엇이 바뀌었는지.
     * 판정은 검사 당시 기준 그대로 두고, 다시 검사는 지원자 비교에서 한다 (평가 기준 2026-10-28, 조건 2026-11-09).
     */
    public Map<Long, Set<Change>> changes(Long positionId) {
        Position position = positionService.get(positionId);
        List<EvalCriterion> all = positionService.allCriteria(positionId);
        List<EvalCriterion> current = all.stream().filter(c -> !"Y".equals(c.getDeletedYn())).toList();
        Map<Long, Set<Change>> out = new LinkedHashMap<>();
        for (ExtractionSummary s : candidateMapper.findByPositionId(positionId)) {
            // 다시 검사가 실패했어도(서버 재시작 등) 예전 결과가 남아 있으면 대상이다. 검사 중이거나 가리기 전은 뺀다
            if (s.getStatus() != CandidateStatus.ANALYZED && s.getStatus() != CandidateStatus.DONE
                    && s.getStatus() != CandidateStatus.FAILED) {
                continue;
            }
            Candidate c = candidateService.get(s.getCandidateId());
            Set<Change> changes = EnumSet.noneOf(Change.class);
            List<Evidence> evidences = evidenceMapper.findByCandidateId(c.getId());
            if (!evidences.isEmpty()
                    && ReportService.criteriaChanged(ReportService.checkedCriteria(c, all, evidences), current)) {
                changes.add(Change.CRITERIA);
            }
            changes.addAll(conditionChanges(CareerEvalService.parse(c.getCareerEvalJson()), position));
            if (!changes.isEmpty()) {
                out.put(c.getId(), changes);
            }
        }
        return out;
    }

    /** 경력·학력 판단에 쓴 조건이 지금 포지션 조건과 다른지. 판단을 저장하기 전 검사(옛 지원자)는 경력 개월이 있을 때만 경력 대상 */
    static Set<Change> conditionChanges(CareerEvalResult eval, Position position) {
        Set<Change> out = EnumSet.noneOf(Change.class);
        if (eval == null) {
            return out;
        }
        if (eval.getCareerRequirement() != null) {
            if (!norm(eval.getCareerRequirement()).equals(norm(position.getRequiredExperience()))
                    || !java.util.Objects.equals(eval.getCareerMinMonths(), position.getCareerMinMonths())
                    || !java.util.Objects.equals(eval.getCareerMaxMonths(), position.getCareerMaxMonths())) {
                out.add(Change.CAREER);
            }
        } else if (eval.getTotalMonths() != null) {
            out.add(Change.CAREER);
        }
        if ((eval.getRequiredEducation() != null || eval.getRequiredMajor() != null)
                && (!norm(eval.getRequiredEducation()).equals(norm(position.getRequiredEducation()))
                        || !norm(eval.getRequiredMajor()).equals(norm(position.getRequiredMajor())))) {
            out.add(Change.EDUCATION);
        }
        return out;
    }

    private static String norm(String s) {
        return s == null ? "" : s.strip();
    }
}
