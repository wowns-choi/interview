package com.example.interview.service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.QuestionOrigin;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.mapper.InterviewQuestionMapper;
import com.example.interview.resume.PdfTextExtractor;
import com.example.interview.resume.PiiMasker;
import com.example.interview.resume.ResumeNameDetector;
import com.example.interview.resume.ResumeProcessingException;
import com.example.interview.web.dto.ExtractionSummary;
import com.example.interview.web.dto.QuestionOrder;

import lombok.extern.slf4j.Slf4j;

/**
 * 지원자 단위 작업: 이력서 업로드 → 포지션 적합도 검사 → 면접 질문 추출, 그리고 질문지 편집.
 * AI 단계가 실패하면 지원자는 FAILED로 남고 화면에서 다시 시도할 수 있다.
 */
@Slf4j
@Service
public class ExtractionService {

    private static final int MAX_ANSWER = 20_000;
    private static final int MAX_QUESTION = 2_000;

    private final PositionService positionService;
    private final CandidateService candidateService;
    private final AnalysisService analysisService;
    private final CandidateMapper candidateMapper;
    private final InterviewQuestionMapper questionMapper;
    private final PdfTextExtractor pdfTextExtractor;
    private final ResumeNameDetector nameDetector;
    private final PiiMasker piiMasker;
    private final TransactionTemplate transactionTemplate;
    private final long maxSizeBytes;

    public ExtractionService(PositionService positionService, CandidateService candidateService,
            AnalysisService analysisService, CandidateMapper candidateMapper, InterviewQuestionMapper questionMapper,
            PdfTextExtractor pdfTextExtractor, ResumeNameDetector nameDetector, PiiMasker piiMasker,
            TransactionTemplate transactionTemplate, @Value("${upload.max-size-mb:10}") int maxSizeMb) {
        this.positionService = positionService;
        this.candidateService = candidateService;
        this.analysisService = analysisService;
        this.candidateMapper = candidateMapper;
        this.questionMapper = questionMapper;
        this.pdfTextExtractor = pdfTextExtractor;
        this.nameDetector = nameDetector;
        this.piiMasker = piiMasker;
        this.transactionTemplate = transactionTemplate;
        this.maxSizeBytes = maxSizeMb * 1024L * 1024L;
    }

    /**
     * 이력서를 저장하고 포지션 적합도 검사를 한다. PDF에 문제가 있으면 아무것도 저장하지 않는다.
     *
     * @return 지원자 id
     */
    public Long uploadResume(Long positionId, MultipartFile resume) {
        if (positionService.criteria(positionId).isEmpty()) {
            throw new BusinessException("평가항목이 없습니다. 평가항목을 먼저 추출하세요.");
        }
        String text = pdfTextExtractor.extract(readPdf(resume));
        Optional<String> name = nameDetector.detect(text);
        String masked = piiMasker.mask(text, name.orElse(null));

        Long candidateId = candidateService.create(positionId, name.orElseGet(() -> baseName(resume)), masked);
        checkFit(candidateId);
        return candidateId;
    }

    public void checkFit(Long candidateId) {
        runAi(candidateId, () -> analysisService.analyzeEvidence(candidateId));
    }

    public void generateQuestions(Long candidateId) {
        runAi(candidateId, () -> analysisService.generateQuestions(candidateId));
    }

    public List<ExtractionSummary> candidates(Long positionId) {
        return candidateMapper.findByPositionId(positionId);
    }

    public void deleteCandidate(Long candidateId) {
        candidateService.get(candidateId);
        candidateMapper.delete(candidateId);
    }

    /** 면접 중 기록한 답변을 저장한다. 빈 값이면 지운다. */
    public void saveAnswer(Long candidateId, Long questionId, String answer) {
        String value = answer == null || answer.isBlank() ? null : answer;
        if (value != null && value.length() > MAX_ANSWER) {
            throw new BusinessException("답변은 " + MAX_ANSWER + "자까지 저장할 수 있습니다.");
        }
        found(questionMapper.updateAnswer(candidateId, questionId, value), questionId);
    }

    public Long addQuestion(Long candidateId, String section, String content) {
        candidateService.get(candidateId);
        if (section == null || section.isBlank()) {
            throw new BusinessException("섹션을 지정하세요.");
        }
        InterviewQuestion q = new InterviewQuestion();
        q.setCandidateId(candidateId);
        q.setSection(section.strip());
        q.setOrigin(QuestionOrigin.MANUAL);
        q.setContent(requireContent(content));
        q.setSortOrder(questionMapper.maxSortOrder(candidateId) + 1);
        questionMapper.insert(q);
        return q.getId();
    }

    public void updateQuestion(Long candidateId, Long questionId, String content) {
        found(questionMapper.updateContent(candidateId, questionId, requireContent(content)), questionId);
    }

    public void deleteQuestion(Long candidateId, Long questionId) {
        found(questionMapper.delete(candidateId, questionId), questionId);
    }

    /** 화면의 질문지 전체 순서를 저장한다. 모든 질문이 정확히 한 번씩 있어야 한다. */
    public void reorderQuestions(Long candidateId, List<QuestionOrder> board) {
        Set<Long> existing = questionMapper.findByCandidateId(candidateId).stream()
                .map(InterviewQuestion::getId).collect(Collectors.toSet());
        Set<Long> seen = new HashSet<>();
        for (QuestionOrder s : board) {
            if (s.section() == null || s.section().isBlank() || s.questionIds() == null) {
                throw new BusinessException("순서 정보가 올바르지 않습니다. 새로고침 후 다시 시도하세요.");
            }
            for (Long id : s.questionIds()) {
                if (!existing.contains(id) || !seen.add(id)) {
                    throw new BusinessException("질문 목록이 바뀌었습니다. 새로고침 후 다시 시도하세요.");
                }
            }
        }
        if (!seen.equals(existing)) {
            throw new BusinessException("질문 목록이 바뀌었습니다. 새로고침 후 다시 시도하세요.");
        }
        transactionTemplate.executeWithoutResult(status -> {
            int order = 1;
            for (QuestionOrder s : board) {
                for (Long id : s.questionIds()) {
                    questionMapper.updatePlace(candidateId, id, s.section().strip(), order++);
                }
            }
        });
    }

    private void runAi(Long candidateId, Runnable step) {
        candidateService.get(candidateId);
        try {
            step.run();
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI 단계 실패 candidateId={}: {}", candidateId, e.getMessage());
        }
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("질문 내용을 입력하세요.");
        }
        String s = content.strip();
        if (s.length() > MAX_QUESTION) {
            throw new BusinessException("질문은 " + MAX_QUESTION + "자까지 쓸 수 있습니다.");
        }
        return s;
    }

    private static void found(int updated, Long questionId) {
        if (updated == 0) {
            throw new NotFoundException("질문을 찾을 수 없습니다: " + questionId);
        }
    }

    private byte[] readPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResumeProcessingException("이력서 PDF 파일을 선택하세요.");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new ResumeProcessingException("파일이 너무 큽니다. 최대 " + (maxSizeBytes / 1024 / 1024) + "MB까지 올릴 수 있습니다.");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf")) {
            throw new ResumeProcessingException("PDF 파일만 지원합니다.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResumeProcessingException("업로드된 파일을 읽을 수 없습니다.", e);
        }
    }

    /** 이름을 못 찾았을 때 화면 표시용: 파일 이름에서 확장자를 뗀 것 */
    private static String baseName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "지원자";
        }
        name = name.replaceAll("^.*[\\\\/]", "");
        return name.toLowerCase().endsWith(".pdf") ? name.substring(0, name.length() - 4) : name;
    }
}
