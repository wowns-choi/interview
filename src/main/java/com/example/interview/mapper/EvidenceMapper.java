package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.example.interview.domain.Evidence;

@Mapper
public interface EvidenceMapper {
    List<Evidence> findByCandidateId(Long candidateId);

    void insert(Evidence evidence);

    void deleteByCandidateId(Long candidateId);
}
