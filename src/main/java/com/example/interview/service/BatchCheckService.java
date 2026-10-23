package com.example.interview.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.interview.domain.CandidateStatus;
import com.example.interview.mapper.CandidateMapper;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 포지션 적합도 검사(다수): 지원자마다 개인정보 가리기 → 적합도 검사를 한 명씩 차례로 서버 뒤에서 돌린다.
 * 사람이 가린 글을 검토하는 단계는 없다 (사용자 결정). 가리고 남은 주민번호·전화번호·이메일 모양이 있으면 그 지원자는 실패로 멈춘다.
 * 원문은 DB에 넣지 않고 메모리에만 두므로, 서버가 다시 켜지면 끝나지 못한 지원자는 실패로 바꾼다.
 */
@Slf4j
@Service
public class BatchCheckService {

    static final String INTERRUPTED = "서버가 다시 시작되어 검사가 중단됐습니다. 다시 올려 주세요.";

    private final ExtractionService extractionService;
    private final CandidateService candidateService;
    private final PositionService positionService;
    private final ResumeAnonymizer resumeAnonymizer;
    private final CandidateMapper candidateMapper;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "batch-check");
        t.setDaemon(true);
        return t;
    });

    public BatchCheckService(ExtractionService extractionService, CandidateService candidateService,
            PositionService positionService, ResumeAnonymizer resumeAnonymizer, CandidateMapper candidateMapper) {
        this.extractionService = extractionService;
        this.candidateService = candidateService;
        this.positionService = positionService;
        this.resumeAnonymizer = resumeAnonymizer;
        this.candidateMapper = candidateMapper;
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
        List<String> texts = new ArrayList<>();
        for (List<MultipartFile> files : chosen) {
            texts.add(extractionService.combine(files));
        }
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            Long id = candidateService.createQueued(positionId, ExtractionService.baseName(chosen.get(i).get(0)));
            String text = texts.get(i);
            ids.add(id);
            worker.submit(() -> process(positionId, id, text));
        }
        return ids;
    }

    private void process(Long positionId, Long candidateId, String text) {
        if (candidateMapper.findById(candidateId) == null) {
            return; // 기다리는 동안 삭제됨
        }
        try {
            candidateMapper.updateStatus(candidateId, CandidateStatus.MASKING, null);
            ResumeAnonymizer.Result masked = resumeAnonymizer.anonymize(positionId, text);
            String name = masked.applicantName().orElse(candidateService.get(candidateId).getDisplayName());
            candidateMapper.updateMasked(candidateId, name.length() > 50 ? name.substring(0, 50) : name, masked.text(),
                    ExtractionService.profileJson(masked));
        } catch (RuntimeException e) {
            log.warn("다수 검사: 개인정보 가리기 실패 candidateId={}: {}", candidateId, e.getMessage());
            candidateMapper.updateStatus(candidateId, CandidateStatus.FAILED,
                    "개인정보를 가리지 못했습니다. 이력서를 다시 올려 주세요. " + e.getMessage());
            return;
        }
        try {
            extractionService.checkFit(candidateId);
        } catch (RuntimeException e) {
            log.warn("다수 검사: 적합도 검사 실패 candidateId={}: {}", candidateId, e.getMessage());
        }
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
