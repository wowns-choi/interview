package com.example.interview.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.interview.config.AiProperties;
import com.example.interview.resume.QuoteVerifier;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateProfile;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.Position;
import com.example.interview.domain.QuestionOrigin;
import com.example.interview.domain.QuestionTemplate;
import com.example.interview.domain.QuestionType;
import com.example.interview.mapper.AiCallLogMapper;
import com.example.interview.mapper.EvidenceMapper;
import com.example.interview.mapper.InterviewQuestionMapper;
import com.example.interview.mapper.QuestionTemplateMapper;
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
    private final QuestionTemplateMapper templateMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final AiProperties aiProperties;
    private final QuoteVerifier quoteVerifier;

    public ResultReport report(Long candidateId) {
        Candidate candidate = candidateService.get(candidateId);
        Position position = positionService.get(candidate.getPositionId());
        List<EvalCriterion> criteria = positionService.criteria(position.getId());
        List<Evidence> evidences = evidenceMapper.findByCandidateId(candidateId);
        // 못 찾은 인용은 지금 규칙으로 다시 대조하고 ("…"로 줄인 인용은 2026-10-26부터 통과),
        // 그래도 없으면 가장 비슷한 원문을 저장하기 전에 검사한 근거는 여기서 찾아 보여 준다 (저장은 하지 않는다)
        evidences.stream().filter(e -> !"Y".equals(e.getVerifiedYn()) && e.getQuote() != null)
                .forEach(e -> {
                    String same;
                    if (quoteVerifier.verify(e.getQuote(), candidate.getMaskedText())) {
                        e.setVerifiedYn("Y");
                        e.setClosestQuote(null);
                    } else if ((same = quoteVerifier.sameSpan(e.getQuote(), candidate.getMaskedText())) != null) {
                        e.setQuote(same); // 몇 글자만 다르게 옮긴 인용은 원문 구간을 보여 준다
                        e.setVerifiedYn("Y");
                        e.setClosestQuote(null);
                    } else if (e.getClosestQuote() == null) {
                        e.setClosestQuote(quoteVerifier.closest(e.getQuote(), candidate.getMaskedText()));
                    }
                });

        List<InterviewQuestion> questions = questionMapper.findByCandidateId(candidateId);
        Map<Long, String> criterionNames = criteria.stream()
                .collect(Collectors.toMap(EvalCriterion::getId, EvalCriterion::getName));
        Map<Long, Evidence> evidenceById = new LinkedHashMap<>();
        evidences.forEach(e -> evidenceById.put(e.getId(), e));

        return new ResultReport(position, candidate, parseProfile(candidate.getProfileJson()),
                CareerEvalService.parse(candidate.getCareerEvalJson()),
                evidenceByCriterion(criteria, evidences),
                questionSheet(questions, criterionNames, oldTemplateTexts(questions)),
                extractedByType(questions, criterionNames),
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
    /** 원문을 따로 저장하기 전에 만든 맞춤 질문은 공통 질문 설정에서 원문을 찾는다 (지금 설정이라 그 뒤 고쳤으면 다를 수 있다) */
    private Map<Long, String> oldTemplateTexts(List<InterviewQuestion> questions) {
        boolean needed = questions.stream().anyMatch(q -> q.getOrigin() == QuestionOrigin.COMMON_PERSONALIZED
                && q.getOriginalContent() == null && q.getTemplateId() != null);
        return needed ? templateMapper.findAll().stream()
                .collect(Collectors.toMap(QuestionTemplate::getId, QuestionTemplate::getContent)) : Map.of();
    }

    private static List<SectionView> questionSheet(List<InterviewQuestion> questions, Map<Long, String> criterionNames,
            Map<Long, String> templateTexts) {
        List<SectionView> sections = new ArrayList<>();
        String current = null;
        List<QuestionView> group = null;
        for (InterviewQuestion q : questions) {
            if (!q.isMerged()) {
                continue;
            }
            if (group == null || !Objects.equals(current, q.getSection())) {
                current = q.getSection();
                group = new ArrayList<>();
                sections.add(new SectionView(current, group));
            }
            group.add(view(q, criterionNames, templateTexts));
        }
        return sections;
    }

    /** 아직 질문지에 넣지 않은 추출 질문을 유형 순서(거짓말 판독기 → … → 해본 적 있어?)로 묶는다. */
    private static List<SectionView> extractedByType(List<InterviewQuestion> questions, Map<Long, String> criterionNames) {
        Map<String, List<QuestionView>> byLabel = new LinkedHashMap<>();
        for (QuestionType t : QuestionType.values()) {
            for (InterviewQuestion q : questions) {
                if (!q.isMerged() && q.getQuestionType() == t) {
                    // 직접 적은 유형은 적은 이름마다 따로 (기본 유형 뒤에)
                    String label = t == QuestionType.CUSTOM && q.getTypeLabel() != null ? q.getTypeLabel() : t.getLabel();
                    byLabel.computeIfAbsent(label, k -> new ArrayList<>()).add(view(q, criterionNames, Map.of()));
                }
            }
        }
        List<SectionView> groups = new ArrayList<>();
        byLabel.forEach((label, list) -> groups.add(new SectionView(label, list)));
        return groups;
    }

    private static QuestionView view(InterviewQuestion q, Map<Long, String> criterionNames, Map<Long, String> templateTexts) {
        String original = null;
        if (q.getOrigin() == QuestionOrigin.COMMON_PERSONALIZED) {
            original = q.getOriginalContent() != null ? q.getOriginalContent() : templateTexts.get(q.getTemplateId());
        }
        return new QuestionView(q, parseFollowUps(q.getFollowUps()),
                q.getCriterionId() == null ? null : criterionNames.get(q.getCriterionId()), original);
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
