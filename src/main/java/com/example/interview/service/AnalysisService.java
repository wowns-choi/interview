package com.example.interview.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.interview.ai.dto.ArrangeResult;
import com.example.interview.ai.dto.EvidenceResult;
import com.example.interview.ai.dto.QuestionResult;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateStatus;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.QuestionOrigin;
import com.example.interview.domain.Position;
import com.example.interview.domain.QuestionSection;
import com.example.interview.domain.QuestionTemplate;
import com.example.interview.domain.QuestionType;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.mapper.EvidenceMapper;
import com.example.interview.mapper.InterviewQuestionMapper;
import com.example.interview.mapper.QuestionSectionMapper;
import com.example.interview.mapper.QuestionTemplateMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 지원자 분석 두 단계: 포지션 적합도 검사(AI ② 근거 분석) → 면접 질문 추출(AI ③-1, 여러 번) → 공통 질문과 합치기(AI ③-2 배치 → 질문지 조립).
 * AI 호출은 DB 트랜잭션 밖에서 하고, 결과 교체만 트랜잭션으로 묶는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final CandidateService candidateService;
    private final PositionService positionService;
    private final EvidenceService evidenceService;
    private final QuestionService questionService;
    private final QuestionSheetAssembler assembler;
    private final CandidateMapper candidateMapper;
    private final EvidenceMapper evidenceMapper;
    private final InterviewQuestionMapper questionMapper;
    private final QuestionSectionMapper sectionMapper;
    private final QuestionTemplateMapper templateMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 근거를 새로 찾는다. 이전 근거에 기대던 질문지도 함께 지운다.
     *
     * @return 같은 호출에서 계산한 총 경력·관련 경력 (저장은 CareerEvalService가 한다)
     */
    public EvidenceResult.Career analyzeEvidence(Long candidateId) {
        Candidate candidate = candidateService.get(candidateId);
        Position position = positionService.get(candidate.getPositionId());
        List<EvalCriterion> criteria = criteria(candidate);

        candidateMapper.updateStatus(candidateId, CandidateStatus.ANALYZING, null);
        try {
            EvidenceService.Analysis analysis = evidenceService.analyze(candidate, position, criteria);
            List<Evidence> evidences = analysis.evidences();
            transactionTemplate.executeWithoutResult(status -> {
                evidenceMapper.deleteByCandidateId(candidateId);
                questionMapper.deleteByCandidateId(candidateId);
                candidateMapper.updateOpinion(candidateId, null);
                evidences.forEach(evidenceMapper::insert);
                candidateMapper.updateStatus(candidateId, CandidateStatus.ANALYZED, null);
            });
            return analysis.career();
        } catch (RuntimeException e) {
            log.error("근거 분석 실패 candidateId={}", candidateId, e);
            candidateMapper.updateStatus(candidateId, CandidateStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    /**
     * 고른 유형의 질문을 추출해 "추출한 질문" 목록에 쌓는다. 질문지(공통 질문과 합친 것)는 건드리지 않는다.
     * 실패해도 지원자 상태는 그대로 두고 화면에 오류만 알린다.
     */
    public Extraction extractQuestions(Long candidateId, Collection<QuestionType> types) {
        Candidate candidate = candidateService.get(candidateId);
        Position position = positionService.get(candidate.getPositionId());
        List<EvalCriterion> criteria = criteria(candidate);
        List<Evidence> evidences = evidenceMapper.findByCandidateId(candidateId);
        if (evidences.isEmpty()) {
            throw new BusinessException("포지션 적합도 검사를 먼저 하세요.");
        }
        List<QuestionType> chosen = QuestionType.SELECTABLE.stream().filter(types::contains).toList();
        if (chosen.isEmpty()) {
            throw new BusinessException("추출할 질문 유형을 고르세요.");
        }
        List<QuestionType> possible = chosen.stream()
                .filter(t -> t.isClaim() ? !QuestionService.claimRefs(evidences).isEmpty()
                        : !QuestionService.experienceCriteria(criteria, evidences).isEmpty())
                .toList();
        if (possible.isEmpty()) {
            throw new BusinessException(chosen.contains(QuestionType.EXPERIENCE)
                    ? "모든 평가항목에 직접 근거가 있어 '해본 적 있어?' 질문을 만들 평가항목이 없습니다."
                    : "직접·간접 근거가 없어 이력서 내용에 대한 질문을 만들 수 없습니다.");
        }

        List<InterviewQuestion> existing = questionMapper.findByCandidateId(candidateId);
        QuestionResult extracted;
        try {
            extracted = questionService.generate(position, candidate, criteria, evidences, possible, existing);
        } catch (RuntimeException e) {
            log.error("질문 추출 실패 candidateId={}", candidateId, e);
            throw new BusinessException("질문을 추출하지 못했습니다. " + e.getMessage());
        }

        int order = questionMapper.maxSortOrder(candidateId);
        List<InterviewQuestion> rows = new ArrayList<>();
        for (QuestionResult.Item item : extracted.getQuestions()) {
            InterviewQuestion q = new InterviewQuestion();
            q.setCandidateId(candidateId);
            q.setSection("");
            q.setOrigin(QuestionOrigin.CUSTOM);
            q.setQuestionType(QuestionType.valueOf(item.getQuestionType()));
            q.setContent(item.getContent().strip());
            q.setIntent(item.getIntent());
            q.setGoodSignal(item.getGoodSignal());
            q.setWarningSignal(item.getWarningSignal());
            q.setFollowUps(JSON.writeValueAsString(item.getFollowUps() == null ? List.of() : item.getFollowUps()));
            q.setCriterionId(item.getCriterionId());
            q.setEvidenceId(item.getEvidenceId() == null ? null
                    : evidences.get(item.getEvidenceId().intValue() - 1).getId());
            q.setPriority(item.getPriority());
            q.setSortOrder(++order);
            q.setMerged(false);
            rows.add(q);
        }
        transactionTemplate.executeWithoutResult(status -> rows.forEach(questionMapper::insert));
        return new Extraction(rows.size(), chosen.stream().filter(t -> !possible.contains(t)).toList());
    }

    /** @param skipped 골랐지만 물을 대상이 없어 만들지 않은 유형 */
    public record Extraction(int count, List<QuestionType> skipped) {
    }

    /**
     * 추출해 둔 질문을 질문지에 넣는다. 질문지가 비어 있으면 공통 질문과 합쳐 AI가 순서·섹션을 정하고(③-2),
     * 이미 있으면 기존 순서·답변은 그대로 두고 새 질문만 같은 평가항목 질문 옆에 끼워 넣는다(AI 호출 없음).
     */
    public void mergeQuestions(Long candidateId) {
        Candidate candidate = candidateService.get(candidateId);
        List<InterviewQuestion> all = questionMapper.findByCandidateId(candidateId);
        List<InterviewQuestion> sheet = all.stream().filter(InterviewQuestion::isMerged).toList();
        List<InterviewQuestion> pool = all.stream().filter(q -> !q.isMerged()).toList();
        if (pool.isEmpty() && !sheet.isEmpty()) {
            throw new BusinessException("질문지에 넣을 새 질문이 없습니다.");
        }

        List<InterviewQuestion> merged;
        if (sheet.isEmpty()) {
            List<EvalCriterion> criteria = criteria(candidate);
            List<QuestionSection> sections = sectionMapper.findAll();
            List<QuestionTemplate> templates = templateMapper.findActive();
            if (pool.isEmpty() && templates.isEmpty()) {
                throw new BusinessException("합칠 질문이 없습니다. 질문을 먼저 추출하세요.");
            }
            ArrangeResult arrangement;
            try {
                arrangement = questionService.arrange(candidate, criteria, sections, templates, pool);
            } catch (RuntimeException e) {
                log.error("질문지 배치 실패 candidateId={}", candidateId, e);
                throw new BusinessException("공통 질문과 합치지 못했습니다. " + e.getMessage());
            }
            merged = assembler.assemble(candidateId, sections, templates, pool, arrangement);
        } else {
            merged = assembler.insert(sheet, pool);
        }

        transactionTemplate.executeWithoutResult(status -> {
            for (InterviewQuestion q : merged) {
                if (q.getId() == null) {
                    questionMapper.insert(q);
                } else {
                    questionMapper.merge(candidateId, q.getId(), q.getSection(), q.getSortOrder());
                }
            }
            candidateMapper.updateStatus(candidateId, CandidateStatus.DONE, null);
        });
    }

    private List<EvalCriterion> criteria(Candidate candidate) {
        List<EvalCriterion> criteria = positionService.criteria(candidate.getPositionId());
        if (criteria.isEmpty()) {
            throw new BusinessException("평가항목이 없습니다.");
        }
        return criteria;
    }
}
