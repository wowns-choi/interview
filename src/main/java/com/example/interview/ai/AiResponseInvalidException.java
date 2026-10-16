package com.example.interview.ai;

/** AI 응답이 JSON 파싱 또는 필수 필드 검증에 실패했을 때. 재요청 대상이다. */
public class AiResponseInvalidException extends RuntimeException {

    public AiResponseInvalidException(String message) {
        super(message);
    }

    public AiResponseInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
