package com.example.interview.ai;

import java.util.Base64;

/** AI에 함께 보내는 이미지 (예: 채용공고 캡처) */
public record AiImage(String contentType, byte[] data) {

    String dataUrl() {
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(data);
    }
}
