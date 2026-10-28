package com.example.interview.web.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
    /** 면접관이 남긴 면접 기록 */
    boolean interviewed;
    LocalDateTime interviewDate;
    /** PASS, FAIL, null */
    String decision;
    /** 자격증 (로컬 AI가 옮겨 적은 목록에서 코드로 찾는다) */
    boolean infoEngineer;
    boolean sqld;

    /** 지원자 비교의 면접 꼬리표: 면접 날짜와 시·분. 0시 0분(시각을 넣기 전 기록)이면 날짜만 */
    public String getInterviewText() {
        if (interviewDate == null) {
            return "";
        }
        String pattern = interviewDate.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) ? "M월 d일" : "M월 d일 ah시 m분";
        return interviewDate.format(DateTimeFormatter.ofPattern(pattern, Locale.KOREAN));
    }

    /** 지원자 고르기에서 같은 이름을 가르는 등록 시각 (적합도 검사 버튼을 누른 때) */
    public String getCreatedText() {
        return createdAt == null ? "" : createdAt.format(DateTimeFormatter.ofPattern("M월 d일 ah시 m분 s초", Locale.KOREAN));
    }

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
            case UPLOADED -> "확인 대기"; // 개인정보 엄격 보호 모드: 가린 이력서를 사람이 확인하기 전
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
