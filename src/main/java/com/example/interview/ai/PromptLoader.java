package com.example.interview.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** resources/prompts/*.txt 를 읽는다. 수정 내용이 바로 반영되도록 캐시하지 않는다. */
@Component
public class PromptLoader {

    public String load(String name) {
        // 스레드의 컨텍스트 클래스로더가 아니라 앱 클래스로더로 찾는다 (실행 war에서 공용 스레드 풀은 앱 클래스를 못 본다)
        ClassPathResource resource = new ClassPathResource("prompts/" + name + ".txt", PromptLoader.class.getClassLoader());
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 파일을 읽을 수 없습니다: " + name, e);
        }
    }
}
