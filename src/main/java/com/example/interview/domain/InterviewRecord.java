package com.example.interview.domain;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/** 면접 녹음본으로 만든 질문-답변. 녹음본에 나온 순서 그대로다 */
@Data
public class InterviewRecord {

    /** 만든 시각 ("2026-10-23 14:05") */
    private String createdAt = "";
    private List<QaItem> items = new ArrayList<>();

    @JsonIgnore
    public List<QaItem> getInterviewerItems() {
        return items.stream().filter(q -> !q.isFromCandidate()).toList();
    }

    @JsonIgnore
    public List<QaItem> getCandidateItems() {
        return items.stream().filter(QaItem::isFromCandidate).toList();
    }
}
