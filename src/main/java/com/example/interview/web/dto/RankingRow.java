package com.example.interview.web.dto;

import java.time.LocalDateTime;

import com.example.interview.domain.CandidateStatus;

import lombok.Value;

/** 지원자 비교 한 줄. 점수가 있고 모든 평가항목으로 검사한 지원자만 순위가 있다 */
@Value
public class RankingRow {
    Long candidateId;
    String displayName;
    CandidateStatus status;
    LocalDateTime createdAt;
    /** 적합도 검사 전이면 null */
    FitScore score;
    /** 비교할 수 없으면(검사 전, 재검사 필요) null */
    Integer rank;
    /** 순위에서 뺀 이유 ("경력 미충족", "학력 미충족", "경력·학력 미충족"), 빼지 않았으면 null */
    String exclusion;
    /** 동점일 때 쓰는 포지션 관련 경력(개월) */
    Integer relevantMonths;

    /** 다수 검사가 진행 중인지 (대기, 가리는 중, 검사 중) */
    public boolean isInProgress() {
        return status == CandidateStatus.QUEUED || status == CandidateStatus.MASKING || status == CandidateStatus.ANALYZING;
    }

    /** 점수가 없을 때 목록에 쓰는 상태 */
    public String getStatusLabel() {
        return switch (status) {
            case QUEUED -> "대기";
            case MASKING -> "가리는 중";
            case ANALYZING -> "검사 중";
            case FAILED -> "실패";
            default -> "검토 중";
        };
    }

    /** RANKED, EXCLUDED(경력·학력 미충족으로 뺌), STALE(평가항목이 바뀌어 다시 검사해야 함), UNCHECKED */
    public String getState() {
        if (score == null) {
            return "UNCHECKED";
        }
        if (!score.isComplete()) {
            return "STALE";
        }
        return exclusion == null ? "RANKED" : "EXCLUDED";
    }
}
