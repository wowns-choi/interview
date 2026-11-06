package com.example.interview.ai;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 앱이 켜지면 로컬 모델을 뒤에서 미리 올려, 첫 이력서의 개인정보 찾기가 모델 올리는 시간(4~5초)을 기다리지 않게 한다. */
@Component
public class LocalAiWarmup {

    private final AiClient aiClient;

    public LocalAiWarmup(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread t = new Thread(aiClient::warmUpLocal, "local-ai-warmup");
        t.setDaemon(true);
        t.start();
    }
}
