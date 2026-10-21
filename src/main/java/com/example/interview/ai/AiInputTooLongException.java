package com.example.interview.ai;

/** 로컬 AI 입력 한도(num_ctx)를 넘어 입력이 잘린 경우. 호출하는 쪽에서 글을 나눠 다시 부를 수 있다. */
public class AiInputTooLongException extends AiCallException {

    public AiInputTooLongException(String message) {
        super(message);
    }
}
