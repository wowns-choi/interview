package com.example.interview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로컬 AI(Ollama) 설정. 이력서 원문은 이쪽으로만 보내 개인정보를 찾는다.
 *
 * @param numCtx 입력+출력 토큰 한도. 이를 넘으면 Ollama가 입력을 잘라내므로 호출을 실패로 본다
 * @param think  추론 모드. Gemma 4는 끄면 7배 빠르지만 추천인 이름·전각 전화번호 등을 놓쳐 켜 둔다.
 *               추론 모드가 없는 모델이면 비워 둔다 (null이면 보내지 않음)
 */
@ConfigurationProperties("local-ai")
public record LocalAiProperties(
        String baseUrl,
        String model,
        Integer numCtx,
        Integer timeoutSeconds,
        Boolean think) {

    public LocalAiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");
        if (model == null || model.isBlank()) {
            model = "gemma4:e4b";
        }
        if (numCtx == null) {
            numCtx = 8192;
        }
        if (timeoutSeconds == null) {
            timeoutSeconds = 120;
        }
    }
}
