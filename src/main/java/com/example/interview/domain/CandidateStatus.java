package com.example.interview.domain;

public enum CandidateStatus {
    /** 다수 검사에서 차례를 기다림 (원문은 메모리에만 있다) */
    QUEUED,
    /** 다수 검사에서 개인정보를 가리는 중 */
    MASKING,
    UPLOADED, ANALYZING, ANALYZED, DONE, FAILED
}
