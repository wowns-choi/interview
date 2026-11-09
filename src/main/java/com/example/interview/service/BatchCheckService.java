package com.example.interview.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateFile;
import com.example.interview.domain.CandidateStatus;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.resume.ResumeProcessingException;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 포지션 적합도 검사(다수): 지원자마다 개인정보 가리기 → 적합도 검사를 한 명씩 차례로 서버 뒤에서 돌린다.
 * 사람이 가린 글을 검토하는 단계는 없다 (사용자 결정). 다만 개인정보 엄격 보호 모드(설정)에서는 한 명씩만 받고, 가린 뒤 멈춰
 * 사람이 확인·수정하고 검사를 누를 때까지 외부 AI로 보내지 않는다 (user 2026-10-27). 가리고 남은 주민번호·전화번호·이메일 모양이 있으면 그 지원자는 실패로 멈춘다.
 * 올린 원본 파일은 다시 내려받을 수 있게 그대로 저장한다 (사용자 결정, 2026-10-26). 뽑은 원문 글은
 * DB에 넣지 않고 메모리에만 두므로, 서버가 다시 켜지면 끝나지 못한 지원자는 실패로 바꾼다.
 */
@Slf4j
@Service
public class BatchCheckService {

    static final String INTERRUPTED = "서버가 다시 시작되어 검사가 중단됐습니다.";

    private final ExtractionService extractionService;
    private final CandidateService candidateService;
    private final PositionService positionService;
    private final ResumeAnonymizer resumeAnonymizer;
    private final CandidateMapper candidateMapper;
    private final PrivacyModeService privacyMode;
    private final RankingService rankingService;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "batch-check");
        t.setDaemon(true);
        return t;
    });

    public BatchCheckService(ExtractionService extractionService, CandidateService candidateService,
            PositionService positionService, ResumeAnonymizer resumeAnonymizer, CandidateMapper candidateMapper,
            PrivacyModeService privacyMode, RankingService rankingService) {
        this.rankingService = rankingService;
        this.extractionService = extractionService;
        this.candidateService = candidateService;
        this.positionService = positionService;
        this.resumeAnonymizer = resumeAnonymizer;
        this.candidateMapper = candidateMapper;
        this.privacyMode = privacyMode;
    }

    /**
     * 지원자별 파일 묶음을 모두 읽고(하나라도 못 읽으면 아무것도 만들지 않는다) 대기 중인 지원자를 만든 뒤 차례로 돌린다.
     *
     * @return 검사를 시작한 지원자 id (올린 순서)
     */
    public List<Long> start(Long positionId, List<List<MultipartFile>> applicants) {
        if (positionService.criteria(positionId).isEmpty()) {
            throw new BusinessException("평가 기준이 없습니다. 평가 기준을 먼저 추출하세요.");
        }
        List<List<MultipartFile>> chosen = applicants.stream()
                .map(files -> files.stream().filter(f -> f != null && !f.isEmpty()).toList())
                .filter(files -> !files.isEmpty())
                .toList();
        if (chosen.isEmpty()) {
            throw new BusinessException("이력서 파일을 올려 주세요.");
        }
        boolean strict = privacyMode.isStrict();
        if (strict && chosen.size() > 1) {
            throw new BusinessException("개인정보 엄격 보호 모드에서는 지원자를 한 명씩 올립니다.");
        }
        long readStart = System.currentTimeMillis();
        List<String> texts = new ArrayList<>();
        for (List<MultipartFile> files : chosen) {
            texts.add(extractionService.combine(files));
        }
        log.info("[시간] 파일 읽기 positionId={} 지원자 {}명 {}ms 글자 {}", positionId, chosen.size(),
                System.currentTimeMillis() - readStart, texts.stream().mapToInt(String::length).sum());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            Long id = candidateService.createQueued(positionId, ExtractionService.baseName(chosen.get(i).get(0)));
            candidateService.saveFiles(id, chosen.get(i));
            String text = texts.get(i);
            ids.add(id);
            long queued = System.currentTimeMillis();
            worker.submit(() -> process(positionId, id, text, strict, queued));
        }
        return ids;
    }

    /**
     * 저장해 둔 원본 파일로 가리기부터 다시 한다 (가리기 전에 멈췄거나 실패한 지원자).
     * 파일을 못 읽으면 상태를 바꾸지 않고 알린다.
     */
    public void restart(Long candidateId) {
        Candidate c = candidateService.get(candidateId);
        if (c.getStatus() == CandidateStatus.QUEUED || c.getStatus() == CandidateStatus.MASKING
                || c.getStatus() == CandidateStatus.ANALYZING) {
            throw new BusinessException("이미 검사 중입니다.");
        }
        List<CandidateFile> files = candidateService.storedFiles(candidateId);
        if (files.isEmpty()) {
            throw new BusinessException("저장된 이력서 파일이 없습니다. 이력서를 다시 올려 주세요.");
        }
        String text;
        try {
            text = extractionService.combineStored(files);
        } catch (ResumeProcessingException e) {
            throw new BusinessException(e.getMessage());
        }
        candidateMapper.updateStatus(candidateId, CandidateStatus.QUEUED, null);
        Long positionId = c.getPositionId();
        boolean strict = privacyMode.isStrict();
        long queued = System.currentTimeMillis();
        worker.submit(() -> process(positionId, candidateId, text, strict, queued));
    }

    /**
     * 검사한 때의 평가 기준이나 경력·학력 조건이 지금과 다른 지원자를 지금 기준으로 차례로 다시 검사한다 (사용자 2026-10-28, 조건 2026-11-09).
     * 평가 기준이 바뀌었으면 전체 검사, 조건만 바뀌었으면 그 판단만 다시 묻는다.
     * 가린 이력서는 그대로 쓰고, 질문지와 답변은 남는다.
     *
     * @return 다시 검사에 넣은 인원
     */
    public int recheckChanged(Long positionId) {
        if (positionService.criteria(positionId).isEmpty()) {
            throw new BusinessException("평가 기준이 없습니다.");
        }
        Map<Long, Set<RankingService.Change>> changes = rankingService.changes(positionId);
        changes.forEach((id, change) -> {
            if (change.contains(RankingService.Change.CRITERIA)) {
                candidateMapper.updateStatus(id, CandidateStatus.QUEUED, null);
                worker.submit(() -> recheck(id));
                return;
            }
            // 조건만 바뀌었으면 그 판단만 다시 묻고 원래 상태로 돌려 둔다 (사용자 2026-11-09)
            Candidate before = candidateService.get(id);
            candidateMapper.updateStatus(id, CandidateStatus.QUEUED, null);
            worker.submit(() -> rejudge(id, change, before.getStatus(), before.getErrorMessage()));
        });
        return changes.size();
    }

    private void rejudge(Long candidateId, Set<RankingService.Change> change, CandidateStatus status, String error) {
        if (candidateMapper.findById(candidateId) == null) {
            return; // 기다리는 동안 삭제됨
        }
        try {
            candidateMapper.updateStatus(candidateId, CandidateStatus.ANALYZING, null);
            extractionService.rejudgeConditions(candidateId, change.contains(RankingService.Change.CAREER),
                    change.contains(RankingService.Change.EDUCATION));
        } finally {
            candidateMapper.updateStatus(candidateId, status, error);
        }
    }

    private void recheck(Long candidateId) {
        if (candidateMapper.findById(candidateId) == null) {
            return; // 기다리는 동안 삭제됨
        }
        try {
            extractionService.checkFit(candidateId);
        } catch (RuntimeException e) {
            log.warn("다시 검사 실패 candidateId={}: {}", candidateId, e.getMessage());
        }
    }

    /** @param strict 개인정보 엄격 보호 모드: 가린 뒤 UPLOADED로 멈추고 검사하지 않는다 */
    private void process(Long positionId, Long candidateId, String text, boolean strict, long queued) {
        if (candidateMapper.findById(candidateId) == null) {
            return; // 기다리는 동안 삭제됨
        }
        long start = System.currentTimeMillis();
        log.info("[시간] 검사 시작 candidateId={} 대기 {}ms 글자 {}", candidateId, start - queued, text.length());
        try {
            candidateMapper.updateStatus(candidateId, CandidateStatus.MASKING, null);
            ResumeAnonymizer.Result masked = resumeAnonymizer.anonymize(positionId, text);
            String name = masked.applicantName().orElse(candidateService.get(candidateId).getDisplayName());
            candidateMapper.updateMasked(candidateId, name.length() > 50 ? name.substring(0, 50) : name, masked.text(),
                    ExtractionService.profileJson(masked));
            log.info("[시간] 개인정보 가리기 끝 candidateId={} {}ms", candidateId, System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            log.warn("다수 검사: 개인정보 가리기 실패 candidateId={}: {}", candidateId, e.getMessage());
            candidateMapper.updateStatus(candidateId, CandidateStatus.FAILED,
                    "개인정보를 가리지 못했습니다. " + e.getMessage());
            return;
        }
        if (strict) {
            return; // 사람이 가린 이력서를 확인하고 검사를 누를 때까지 기다린다
        }
        long fitStart = System.currentTimeMillis();
        try {
            extractionService.checkFit(candidateId);
        } catch (RuntimeException e) {
            log.warn("다수 검사: 적합도 검사 실패 candidateId={}: {}", candidateId, e.getMessage());
        }
        long end = System.currentTimeMillis();
        log.info("[시간] 검사 끝 candidateId={} 전체 {}ms = 가리기 {}ms + 적합도 검사 {}ms", candidateId,
                end - start, fitStart - start, end - fitStart);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failUnfinished() {
        int n = candidateMapper.failUnfinished(INTERRUPTED);
        if (n > 0) {
            log.info("끝나지 못한 검사 {}건을 실패로 바꿈", n);
        }
    }

    /** 지금까지 넣은 일이 모두 끝날 때까지 기다린다 (테스트용) */
    void awaitIdle() throws InterruptedException, ExecutionException {
        worker.submit(() -> { }).get();
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
