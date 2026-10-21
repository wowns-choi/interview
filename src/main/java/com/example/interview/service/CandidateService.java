package com.example.interview.service;

import org.springframework.stereotype.Service;

import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateStatus;
import com.example.interview.mapper.CandidateMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateMapper candidateMapper;

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
