package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.example.interview.domain.CandidateFile;

@Mapper
public interface CandidateFileMapper {
    void insert(CandidateFile file);

    /** 화면 표시용: 바이트 없이 */
    List<CandidateFile> findByCandidateId(Long candidateId);

    CandidateFile findById(Long id);
}
