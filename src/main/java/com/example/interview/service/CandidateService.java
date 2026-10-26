package com.example.interview.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateFile;
import com.example.interview.domain.CandidateStatus;
import com.example.interview.mapper.CandidateFileMapper;
import com.example.interview.mapper.CandidateMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateMapper candidateMapper;
    private final CandidateFileMapper fileMapper;

    public Candidate get(Long id) {
        Candidate c = candidateMapper.findById(id);
        if (c == null) {
            throw new NotFoundException("추출 결과를 찾을 수 없습니다: " + id);
        }
        return c;
    }

    /** 다수 검사: 차례를 기다리는 지원자. 가린 글은 가리기가 끝난 뒤 채운다 */
    public Long createQueued(Long positionId, String displayName) {
        Candidate c = new Candidate();
        c.setPositionId(positionId);
        c.setDisplayName(displayName.length() > 50 ? displayName.substring(0, 50) : displayName);
        c.setStatus(CandidateStatus.QUEUED);
        candidateMapper.insert(c);
        return c.getId();
    }

    /** 올린 원본 파일을 그대로 남긴다 (다시 내려받기용, AI에는 보내지 않는다) */
    public void saveFiles(Long candidateId, List<MultipartFile> files) {
        int order = 1;
        for (MultipartFile f : files) {
            CandidateFile file = new CandidateFile();
            file.setCandidateId(candidateId);
            file.setFileName(PositionService.fileName(f.getOriginalFilename() == null ? "" : f.getOriginalFilename()));
            file.setContentType(contentType(file.getFileName()));
            try {
                file.setFileData(f.getBytes());
            } catch (IOException e) {
                throw new BusinessException("파일을 읽지 못했습니다: " + file.getFileName());
            }
            file.setSortOrder(order++);
            fileMapper.insert(file);
        }
    }

    /** 원본 파일 목록 (바이트 없이) */
    public List<CandidateFile> files(Long candidateId) {
        return fileMapper.findByCandidateId(candidateId);
    }

    /** 다시 검사용: 원본 파일을 바이트까지 */
    public List<CandidateFile> storedFiles(Long candidateId) {
        return fileMapper.findWithDataByCandidateId(candidateId);
    }

    public CandidateFile file(Long candidateId, Long fileId) {
        CandidateFile file = fileMapper.findById(fileId);
        if (file == null || !file.getCandidateId().equals(candidateId)) {
            throw new NotFoundException("파일을 찾을 수 없습니다: " + fileId);
        }
        return file;
    }

    /** 브라우저가 준 형식 대신 확장자로 정한다. 모르는 형식은 그냥 바이트로 내려준다 */
    static String contentType(String fileName) {
        String lower = fileName.toLowerCase();
        String ext = lower.substring(lower.lastIndexOf('.') + 1);
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "hwp" -> "application/x-hwp";
            case "hwpx" -> "application/hwp+zip";
            default -> "application/octet-stream";
        };
    }

    /**
     * 목록에 보이는 이름을 바꾼다. 다수 검사가 진행 중이면 가리기가 끝날 때 이름을 덮어쓰므로 끝난 뒤에만 바꾼다.
     *
     * @return 저장한 이름
     */
    public String rename(Long id, String displayName) {
        Candidate c = get(id);
        if (displayName == null || displayName.isBlank()) {
            throw new BusinessException("지원자 이름을 입력하세요.");
        }
        CandidateStatus s = c.getStatus();
        if (s == CandidateStatus.QUEUED || s == CandidateStatus.MASKING || s == CandidateStatus.ANALYZING) {
            throw new BusinessException("검사가 끝난 뒤에 이름을 바꿀 수 있습니다.");
        }
        String name = displayName.strip();
        name = name.length() > 50 ? name.substring(0, 50) : name;
        candidateMapper.rename(id, name);
        return name;
    }

    /**
     * 면접 기록을 저장한다. 날짜나 합격·불합격을 정하면 면접을 본 것으로 본다.
     * 합격·불합격은 면접관이 정한다 (AI는 정하지 않는다).
     *
     * @param decision PASS, FAIL 또는 null(정하지 않음)
     * @return 저장한 값
     */
    public Candidate updateInterview(Long id, boolean interviewed, LocalDate interviewDate, String decision) {
        Candidate c = get(id);
        String d = decision == null || decision.isBlank() ? null : decision.strip();
        if (d != null && !d.equals("PASS") && !d.equals("FAIL")) {
            throw new BusinessException("합격 또는 불합격만 고를 수 있습니다.");
        }
        boolean done = interviewed || interviewDate != null || d != null;
        candidateMapper.updateInterview(id, done, interviewDate, d);
        c.setInterviewed(done);
        c.setInterviewDate(interviewDate);
        c.setDecision(d);
        return c;
    }

    /** 마스킹된 이력서 텍스트만 저장한다. PDF 파일과 마스킹 전 원문은 저장하지 않는다. */
    public Long create(Long positionId, String displayName, String maskedText, String profileJson) {
        Candidate c = new Candidate();
        c.setPositionId(positionId);
        c.setDisplayName(displayName.length() > 50 ? displayName.substring(0, 50) : displayName);
        c.setMaskedText(maskedText);
        c.setProfileJson(profileJson);
        c.setStatus(CandidateStatus.UPLOADED);
        candidateMapper.insert(c);
        return c.getId();
    }
}
