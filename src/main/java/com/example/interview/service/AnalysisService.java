package com.example.interview.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.interview.ai.dto.ArrangeResult;
import com.example.interview.ai.dto.QuestionResult;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateStatus;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.Position;
import com.example.interview.domain.QuestionSection;
import com.example.interview.domain.QuestionTemplate;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.mapper.EvidenceMapper;
import com.example.interview.mapper.InterviewQuestionMapper;
import com.example.interview.mapper.QuestionSectionMapper;
import com.example.interview.mapper.QuestionTemplateMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 지원자 분석 두 단계: 포지션 적합도 검사(AI ② 근거 분석) → 면접 질문 추출(AI ③ 질문 추출 → 배치 → 질문지 조립).
 * AI 호출은 DB 트랜잭션 밖에서 하고, 결과 교체만 트랜잭션으로 묶는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

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

    /** 근거를 새로 찾는다. 이전 근거에 기대던 질문지도 함께 지운다. */
    public void analyzeEvidence(Long candidateId) {
        Candidate candidate = candidateService.get(candidateId);
        List<EvalCriterion> criteria = criteria(candidate);

        candidateMapper.updateStatus(candidateId, CandidateStatus.ANALYZING, null);
        try {
            List<Evidence> evidences = evidenceService.analyze(candidate, criteria);
            transactionTemplate.executeWithoutResult(status -> {
                evidenceMapper.deleteByCandidateId(candidateId);
                questionMapper.deleteByCandidateId(candidateId);
                evidences.forEach(evidenceMapper::insert);
                candidateMapper.updateStatus(candidateId, CandidateStatus.ANALYZED, null);
            });
        } catch (RuntimeException e) {
            log.error("근거 분석 실패 candidateId={}", candidateId, e);
            candidateMapper.updateStatus(candidateId, CandidateStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    public void generateQuestions(Long candidateId) {
        Candidate candidate = candidateService.get(candidateId);
        Position position = positionService.get(candidate.getPositionId());
        List<EvalCriterion> criteria = criteria(candidate);
        List<Evidence> evidences = evidenceMapper.findByCandidateId(candidateId);
        if (evidences.isEmpty()) {
            throw new BusinessException("포지션 적합도 검사를 먼저 하세요.");
        }

        candidateMapper.updateStatus(candidateId, CandidateStatus.ANALYZING, null);
        try {
            QuestionResult extracted = questionService.generate(position, candidate, criteria, evidences);
            List<QuestionSection> sections = sectionMapper.findAll();
            List<QuestionTemplate> templates = templateMapper.findActive();
            ArrangeResult arrangement = questionService.arrange(candidate, criteria, evidences, sections, templates,
                    extracted.getQuestions());

            Set<Long> criterionIds = criteria.stream().map(EvalCriterion::getId).collect(Collectors.toSet());
            Set<Long> evidenceRefs = new HashSet<>();
            for (long ref = 1; ref <= evidences.size(); ref++) {
                evidenceRefs.add(ref);
            }
            List<InterviewQuestion> sheet = assembler.assemble(candidateId, sections, templates, extracted.getQuestions(),
                    arrangement, criterionIds, evidenceRefs);
            for (InterviewQuestion q : sheet) {
                if (q.getEvidenceId() != null) {
                    q.setEvidenceId(evidences.get(q.getEvidenceId().intValue() - 1).getId());
                }
            }

            transactionTemplate.executeWithoutResult(status -> {
                questionMapper.deleteByCandidateId(candidateId);
                sheet.forEach(questionMapper::insert);
                candidateMapper.updateStatus(candidateId, CandidateStatus.DONE, null);
            });
        } catch (RuntimeException e) {
            log.error("질문 생성 실패 candidateId={}", candidateId, e);
            candidateMapper.updateStatus(candidateId, CandidateStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    private List<EvalCriterion> criteria(Candidate candidate) {
        List<EvalCriterion> criteria = positionService.criteria(candidate.getPositionId());
        if (criteria.isEmpty()) {
            throw new BusinessException("평가항목이 없습니다.");
        }
        return criteria;
    }
}
