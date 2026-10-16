package com.example.interview.domain;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class Position {
    private Long id;
    private String title;
    private String jdText;
    /** 첨부 이미지에서 AI가 읽어 낸 공고 내용 (이미지가 없으면 null) */
    private String imageText;
    private PositionStatus status = PositionStatus.DRAFT;
    private LocalDateTime createdAt;

    public boolean isConfirmed() {
        return status == PositionStatus.CONFIRMED;
    }

    /** 뒤 단계(질문 생성)에 보내는 공고 전문: 입력한 글 + 이미지에서 읽은 내용 */
    public String getPostingText() {
        String text = jdText == null ? "" : jdText;
        if (imageText == null || imageText.isBlank()) {
            return text;
        }
        return text.isBlank() ? imageText : text + "\n\n[이미지 내용]\n" + imageText;
    }
}
