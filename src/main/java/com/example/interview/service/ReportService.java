package com.example.interview.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.interview.config.AiProperties;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateProfile;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.Position;
import com.example.interview.mapper.AiCallLogMapper;
import com.example.interview.mapper.EvidenceMapper;
import com.example.interview.mapper.InterviewQuestionMapper;
import com.example.interview.web.dto.AiUsageSummary;
import com.example.interview.web.dto.CriterionEvidenceView;
import com.example.interview.web.dto.QuestionView;
import com.example.interview.web.dto.ResultReport;
import com.example.interview.web.dto.SectionView;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 추출 결과(평가항목, 근거, 질문지, AI 사용량)를 화면·PDF용으로 모은다. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final CandidateService candidateService;
    private final PositionService positionService;
    private final EvidenceMapper evidenceMapper;
    private final InterviewQuestionMapper questionMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final AiProperties aiProperties;

    public ResultReport report(Long candidateId) {
        Candidate candidate = candidateService.get(candidateId);
        Position position = positionService.get(candidate.getPositionId());
        List<EvalCriterion> criteria = positionService.criteria(position.getId());
        List<Evidence> evidences = evidenceMapper.findByCandidateId(candidateId);

        Map<Long, Evidence> evidenceById = new LinkedHashMap<>();
        evidences.forEach(e -> evidenceById.put(e.getId(), e));

        return new ResultReport(position, candidate, parseProfile(candidate.getProfileJson()),
                CareerEvalService.parse(candidate.getCareerEvalJson()),
                evidenceByCriterion(criteria, evidences),
                questionSheet(candidateId, criteria),
                evidenceById,
                usage(candidateId, position.getId()));
    }

    /** 평가항목 순서대로, 각 항목의 근거를 직접 → 간접 → 없음 순으로 */
    static List<CriterionEvidenceView> evidenceByCriterion(List<EvalCriterion> criteria, List<Evidence> evidences) {
        Map<Long, List<Evidence>> byCriterion = evidences.stream()
                .collect(Collectors.groupingBy(Evidence::getCriterionId, LinkedHashMap::new, Collectors.toList()));
        List<CriterionEvidenceView> views = new ArrayList<>();
        for (EvalCriterion c : criteria) {
            List<Evidence> list = byCriterion.getOrDefault(c.getId(), List.of());
            String point = list.stream().map(Evidence::getInterviewPoint)
                    .filter(p -> p != null && !p.isBlank()).findFirst().orElse(null);
            views.add(new CriterionEvidenceView(c, list, point));
        }
        return views;
    }

    /** 질문지를 섹션별로 묶는다. 질문은 이미 sort_order(섹션 순서 포함) 순이므로 이어지는 같은 섹션끼리 묶는다. */
    private List<SectionView> questionSheet(Long candidateId, List<EvalCriterion> criteria) {
        Map<Long, String> criterionNames = criteria.stream()
                .collect(Collectors.toMap(EvalCriterion::getId, EvalCriterion::getName));
        List<SectionView> sections = new ArrayList<>();
        String current = null;
        List<QuestionView> group = null;
        for (InterviewQuestion q : questionMapper.findByCandidateId(candidateId)) {
            if (group == null || !Objects.equals(current, q.getSection())) {
                current = q.getSection();
                group = new ArrayList<>();
                sections.add(new SectionView(current, group));
            }
            group.add(new QuestionView(q, parseFollowUps(q.getFollowUps()),
                    q.getCriterionId() == null ? null : criterionNames.get(q.getCriterionId())));
        }
        return sections;
    }

    /** 추출 1건의 AI 호출(①, ②, ③) 횟수, 토큰, 소요 시간, 예상 비용 */
    private AiUsageSummary usage(Long candidateId, Long positionId) {
        AiUsageSummary summary = aiCallLogMapper.summarizeForCandidate(candidateId, positionId);
        if (summary == null) {
            summary = new AiUsageSummary();
        }
        if (aiProperties.hasPricing()) {
            summary.setEstimatedCostUsd(summary.getInputTokens() / 1_000_000.0 * aiProperties.inputPricePer1m()
                    + summary.getOutputTokens() / 1_000_000.0 * aiProperties.outputPricePer1m());
        }
        return summary;
    }

    static CandidateProfile parseProfile(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, CandidateProfile.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static List<String> parseFollowUps(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JacksonException e) {
            return List.of(json);
        }
    }
}
