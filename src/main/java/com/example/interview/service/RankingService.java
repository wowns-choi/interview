package com.example.interview.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateProfile;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Position;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.mapper.EvidenceMapper;
import com.example.interview.web.dto.CareerGauge;
import com.example.interview.web.dto.CriterionEvidenceView;
import com.example.interview.web.dto.ExtractionSummary;
import com.example.interview.web.dto.FitScore;
import com.example.interview.web.dto.RankingRow;

import lombok.RequiredArgsConstructor;

/**
 * 포지션 하나의 지원자를 점수 순으로 줄 세운다.
 * 포지션에서 켜 둔 경우 경력·학력 미충족 지원자는 순위 없이 "제외됨"으로 뺀다.
 * 동점이면 직접 근거가 많은 쪽, 그다음 포지션 관련 경력이 긴 쪽이 앞선다.
 * 검사 뒤에 평가항목이 바뀐 지원자(재검사 필요)와 검사 전 지원자는 순위 없이 뒤에 둔다.
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    private static final Comparator<RankingRow> BY_SCORE = Comparator
            .comparingDouble((RankingRow r) -> r.getScore().getTotal()).reversed()
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
        List<EvalCriterion> criteria = positionService.criteria(positionId);
        List<RankingRow> ranked = new ArrayList<>();
        List<RankingRow> excluded = new ArrayList<>();
        List<RankingRow> stale = new ArrayList<>();
        List<RankingRow> unchecked = new ArrayList<>();
        for (ExtractionSummary s : candidateMapper.findByPositionId(positionId)) {
            Candidate c = candidateService.get(s.getCandidateId());
            List<CriterionEvidenceView> views = ReportService.evidenceByCriterion(criteria,
                    evidenceMapper.findByCandidateId(c.getId()));
            FitScore score = null;
            Integer relevant = null;
            if (views.stream().anyMatch(CriterionEvidenceView::isChecked)) {
                CandidateProfile profile = ReportService.parseProfile(c.getProfileJson());
                CareerEvalResult eval = CareerEvalService.parse(c.getCareerEvalJson());
                CareerGauge gauge = CareerGauge.of(profile, position.getRequiredExperience(), eval);
                score = FitScore.of(position, eval, gauge, views);
                relevant = eval == null ? null : eval.getRelevantMonths();
            }
            RankingRow row = new RankingRow(s.getCandidateId(), s.getDisplayName(), s.getStatus(), s.getCreatedAt(),
                    score, null, exclusion(position, score), relevant);
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
                    r.getScore(), i + 1, null, r.getRelevantMonths()));
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
}
