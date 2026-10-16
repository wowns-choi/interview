package com.example.interview.resume;

/** 이력서 처리 중 사용자에게 그대로 보여줄 수 있는 오류 */
public class ResumeProcessingException extends RuntimeException {

    public ResumeProcessingException(String message) {
        super(message);
    }

    public ResumeProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
