package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateStatus;
import com.example.interview.web.dto.ExtractionSummary;

@Mapper
public interface CandidateMapper {
    Candidate findById(Long id);

    List<ExtractionSummary> findByPositionId(Long positionId);

    void insert(Candidate candidate);

    void delete(Long id);

    void updateStatus(@Param("id") Long id, @Param("status") CandidateStatus status,
            @Param("errorMessage") String errorMessage);
}
