package com.example.interview.service;

/** 화면에 그대로 보여줄 수 있는 업무 규칙 위반 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
