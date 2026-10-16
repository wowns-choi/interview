package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.interview.domain.AiCallLog;
import com.example.interview.domain.AiPurpose;
import com.example.interview.web.dto.AiUsageSummary;

@Mapper
public interface AiCallLogMapper {
    void insert(AiCallLog log);

    List<AiCallLog> findByPurposeAndRef(@Param("purpose") AiPurpose purpose, @Param("refId") Long refId);

    /** 추출 1건의 AI 사용량: 평가항목 추출(포지션) + 근거 분석·질문 생성(지원자) */
    AiUsageSummary summarizeForCandidate(@Param("candidateId") Long candidateId, @Param("positionId") Long positionId);
}
