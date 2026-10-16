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

    /** 마스킹된 이력서 텍스트만 저장한다. PDF 파일과 마스킹 전 원문은 저장하지 않는다. */
    public Long create(Long positionId, String displayName, String maskedText) {
        Candidate c = new Candidate();
        c.setPositionId(positionId);
        c.setDisplayName(displayName.length() > 50 ? displayName.substring(0, 50) : displayName);
        c.setMaskedText(maskedText);
        c.setStatus(CandidateStatus.UPLOADED);
        candidateMapper.insert(c);
        return c.getId();
    }
}
