package com.example.interview.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.ai.dto.EvidenceResult;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateFile;
import com.example.interview.domain.CandidateStatus;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.QuestionOrigin;
import com.example.interview.domain.QuestionType;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.mapper.EvidenceMapper;
import com.example.interview.mapper.InterviewQuestionMapper;
import com.example.interview.resume.DocumentTextExtractor;
import com.example.interview.resume.PiiLeakChecker;
import com.example.interview.resume.ResumeProcessingException;
import com.example.interview.web.dto.ExtractionSummary;
import com.example.interview.web.dto.QuestionOrder;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 지원자 단위 작업: 이력서 업로드(개인정보 가리기) → 검토 → 포지션 적합도 검사 → 면접 질문 추출, 그리고 질문지 편집.
 * AI 단계가 실패하면 지원자는 FAILED로 남고 화면에서 다시 시도할 수 있다.
 */
@Slf4j
@Service
public class ExtractionService {

    private static final int MAX_ANSWER = 20_000;
    private static final int MAX_QUESTION = 2_000;
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final PositionService positionService;
    private final CandidateService candidateService;
    private final AnalysisService analysisService;
    private final CareerEvalService careerEvalService;
    private final CandidateMapper candidateMapper;
    private final InterviewQuestionMapper questionMapper;
    private final EvidenceMapper evidenceMapper;
    private final DocumentTextExtractor documentTextExtractor;
    private final ResumeAnonymizer resumeAnonymizer;
    private final PiiLeakChecker leakChecker;
    private final TransactionTemplate transactionTemplate;
    private final long maxSizeBytes;

    public ExtractionService(PositionService positionService, CandidateService candidateService,
            AnalysisService analysisService, CareerEvalService careerEvalService, CandidateMapper candidateMapper,
            InterviewQuestionMapper questionMapper,
            EvidenceMapper evidenceMapper, PiiLeakChecker leakChecker,
            DocumentTextExtractor documentTextExtractor, ResumeAnonymizer resumeAnonymizer,
            TransactionTemplate transactionTemplate, @Value("${upload.max-size-mb:10}") int maxSizeMb) {
        this.positionService = positionService;
        this.candidateService = candidateService;
        this.analysisService = analysisService;
        this.careerEvalService = careerEvalService;
        this.candidateMapper = candidateMapper;
        this.questionMapper = questionMapper;
        this.evidenceMapper = evidenceMapper;
        this.leakChecker = leakChecker;
        this.documentTextExtractor = documentTextExtractor;
        this.resumeAnonymizer = resumeAnonymizer;
        this.transactionTemplate = transactionTemplate;
        this.maxSizeBytes = maxSizeMb * 1024L * 1024L;
    }

    /**
     * 이력서·포트폴리오 파일을 합쳐 개인정보를 가린 글과 프로필만 저장한다 (검토 전 상태). 외부 AI는 부르지 않는다.
     * 파일에 문제가 있거나 개인정보를 가리지 못하면 아무것도 저장하지 않는다.
     *
     * @return 지원자 id
     */
    public Long uploadResume(Long positionId, List<MultipartFile> files) {
        if (positionService.criteria(positionId).isEmpty()) {
            throw new BusinessException("평가 기준이 없습니다. 평가 기준을 먼저 추출하세요.");
        }
        List<MultipartFile> chosen = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (chosen.isEmpty()) {
            throw new ResumeProcessingException("이력서 파일을 선택하세요.");
        }
        String text = combine(chosen);
        ResumeAnonymizer.Result masked = resumeAnonymizer.anonymize(positionId, text);

        return candidateService.create(positionId, masked.applicantName().orElseGet(() -> baseName(chosen.get(0))),
                masked.text(), profileJson(masked));
    }

    /**
     * 사용자가 검토한 글을 저장하고 적합도 검사를 한다. 주민번호·전화번호·이메일 모양이 남아 있으면 저장하지 않는다.
     *
     * @param reviewedText null이면 저장된 글 그대로 다시 검사한다
     */
    public void checkFit(Long candidateId, String reviewedText) {
        if (reviewedText != null) {
            candidateService.get(candidateId);
            String text = reviewedText.replace("\r\n", "\n").strip();
            if (text.isEmpty()) {
                throw new BusinessException("이력서 내용이 비어 있습니다.");
            }
            List<String> leaks = leakChecker.find(text);
            if (!leaks.isEmpty()) {
                throw new BusinessException("가리지 않은 개인정보(" + String.join(", ", leaks) + ")가 있어 검사하지 않았습니다.");
            }
            candidateMapper.updateMaskedText(candidateId, text);
        }
        checkFit(candidateId);
    }

    /**
     * 외부 AI를 세 번 동시에 부른다: 가린 글로 근거와 총 경력·관련 경력(EVIDENCE),
     * 학교 목록으로 학력(EDUCATION_EVAL), 이름표·기간 목록으로 타임라인(TIMELINE). 한쪽이 실패해도 나머지는 저장한다.
     */
    public void checkFit(Long candidateId) {
        runAi(candidateId, () -> {
            Candidate candidate = candidateService.get(candidateId);
            CompletableFuture<CareerEvalResult> education =
                    async(() -> careerEvalService.education(candidate));
            CompletableFuture<List<CareerEvalResult.Period>> timeline =
                    async(() -> careerEvalService.timeline(candidate));
            EvidenceResult.Career career = null;
            try {
                career = analysisService.analyzeEvidence(candidateId);
            } finally {
                careerEvalService.save(candidate, education.join(), timeline.join(), career);
            }
        });
    }

    /** 공용 스레드 풀에서 돌리되, 실행 war에서도 앱 클래스·리소스가 보이도록 앱 클래스로더를 건다 */
    private static <T> CompletableFuture<T> async(Supplier<T> task) {
        ClassLoader app = ExtractionService.class.getClassLoader();
        return CompletableFuture.supplyAsync(() -> {
            Thread thread = Thread.currentThread();
            ClassLoader before = thread.getContextClassLoader();
            thread.setContextClassLoader(app);
            try {
                return task.get();
            } finally {
                thread.setContextClassLoader(before);
            }
        });
    }

    public AnalysisService.Extraction extractQuestions(Long candidateId, Collection<QuestionType> types) {
        return analysisService.extractQuestions(candidateId, types);
    }

    public AnalysisService.Extraction extractQuestionsByNames(Long candidateId, List<String> names) {
        return analysisService.extractQuestionsByNames(candidateId, names);
    }

    public void mergeQuestions(Long candidateId) {
        analysisService.mergeQuestions(candidateId);
    }

    public void startCommonSheet(Long candidateId) {
        analysisService.startCommonSheet(candidateId);
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

    /** 면접에서 물어본 질문으로 체크하거나 푼다. */
    public void checkQuestion(Long candidateId, Long questionId, boolean checked) {
        found(questionMapper.updateChecked(candidateId, questionId, checked), questionId);
    }

    /** 필수 질문으로 표시하거나 푼다. */
    public void markMustAsk(Long candidateId, Long questionId, boolean mustAsk) {
        found(questionMapper.updateMustAsk(candidateId, questionId, mustAsk), questionId);
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

    /** 화면의 질문지 전체 순서를 저장한다. 질문지의 모든 질문(추출만 해 둔 것 제외)이 정확히 한 번씩 있어야 한다. */
    public void reorderQuestions(Long candidateId, List<QuestionOrder> board) {
        Set<Long> existing = questionMapper.findByCandidateId(candidateId).stream()
                .filter(InterviewQuestion::isMerged).map(InterviewQuestion::getId).collect(Collectors.toSet());
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

    /**
     * 파일이 여러 개면 "[첨부 n (형식)]" 줄로 구분해 합친다.
     * 파일 이름에는 지원자 이름이 들어 있는 경우가 많아 글에 넣지 않는다.
     */
    String combine(List<MultipartFile> files) {
        // 형식을 먼저 모두 확인해, 뒤 파일이 잘못돼도 앞 파일을 읽느라 시간을 쓰지 않게 한다
        files.forEach(f -> DocumentTextExtractor.kindOf(f.getOriginalFilename()));
        List<CandidateFile> read = new ArrayList<>();
        for (MultipartFile f : files) {
            CandidateFile c = new CandidateFile();
            c.setFileName(f.getOriginalFilename());
            c.setFileData(read(f));
            read.add(c);
        }
        return combineStored(read);
    }

    /** 저장해 둔 원본 파일(다시 검사)이나 막 올린 파일을 읽어 합친다 */
    String combineStored(List<CandidateFile> files) {
        files.forEach(f -> DocumentTextExtractor.kindOf(f.getFileName()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < files.size(); i++) {
            CandidateFile file = files.get(i);
            DocumentTextExtractor.Extracted doc = documentTextExtractor.extract(file.getFileName(), file.getFileData());
            if (files.size() > 1) {
                sb.append("[첨부 ").append(i + 1).append(" (").append(doc.kind()).append(")]\n");
            }
            sb.append(doc.text()).append("\n\n");
        }
        return sb.toString().strip();
    }

    private byte[] read(MultipartFile file) {
        if (file.getSize() > maxSizeBytes) {
            throw new ResumeProcessingException(file.getOriginalFilename() + ": 파일이 너무 큽니다. 최대 "
                    + (maxSizeBytes / 1024 / 1024) + "MB까지 올릴 수 있습니다.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResumeProcessingException("업로드된 파일을 읽을 수 없습니다.", e);
        }
    }

    static String profileJson(ResumeAnonymizer.Result masked) {
        return masked.profile().isFilled() ? JSON.writeValueAsString(masked.profile()) : null;
    }

    /** 이름을 못 찾았을 때 화면 표시용: 첫 파일 이름에서 확장자를 뗀 것 */
    static String baseName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "지원자";
        }
        name = name.replaceAll("^.*[\\\\/]", "");
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
