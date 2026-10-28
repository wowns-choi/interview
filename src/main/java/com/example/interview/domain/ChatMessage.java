package com.example.interview.domain;

import java.time.LocalDateTime;

import lombok.Data;

/** AI와 대화의 한 마디. stage는 BEFORE(면접 전) 또는 AFTER(면접 후), role은 USER 또는 AI */
@Data
public class ChatMessage {
    private Long id;
    private Long candidateId;
    private String stage;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
