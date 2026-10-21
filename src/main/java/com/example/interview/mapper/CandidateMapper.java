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

    void updateMaskedText(@Param("id") Long id, @Param("maskedText") String maskedText);

    void rename(@Param("id") Long id, @Param("displayName") String displayName);

    void updateCareerEval(@Param("id") Long id, @Param("careerEvalJson") String careerEvalJson);

    void updateStatus(@Param("id") Long id, @Param("status") CandidateStatus status,
            @Param("errorMessage") String errorMessage);

    /** 다수 검사: 가린 글과 프로필을 채우고 검토 전 상태로 */
    void updateMasked(@Param("id") Long id, @Param("displayName") String displayName,
            @Param("maskedText") String maskedText, @Param("profileJson") String profileJson);

    /** 서버가 다시 켜졌을 때 끝나지 못한 검사를 실패로 */
    int failUnfinished(@Param("errorMessage") String errorMessage);
}
