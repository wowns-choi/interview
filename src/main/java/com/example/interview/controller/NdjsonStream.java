package com.example.interview.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * 한 줄에 JSON 하나(NDJSON)를 보내는 스트림 응답.
 * <ul>
 * <li>StreamingResponseBody가 넘겨주는 스트림은 flush를 무시해서(StreamUtils.NonFlushingOutputStream) Tomcat 버퍼 8KB가 찰 때까지
 * 브라우저로 나가지 않았다 (면접 내용 정리가 12개씩 나옴, 2026-11-06). 그래서 서블릿 응답 스트림에 직접 쓰고 줄마다 비운다.</li>
 * <li>보던 사람이 페이지를 떠나도 일은 끝까지 해서 저장한다 (사용자 2026-11-06). 연결이 끊기면 Spring이 응답 스레드를 중단시키므로
 * 일은 따로 스레드에서 하고, 응답 스레드는 줄을 받아 보내기만 한다. 응답이 끝나면 일 쪽은 더 보내지 않는다.</li>
 * </ul>
 */
final class NdjsonStream {

    private static final Logger log = LoggerFactory.getLogger(NdjsonStream.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String END = "";
    /** 실행 war에서도 앱 클래스·리소스(프롬프트)가 보이도록 앱 클래스로더를 건다 */
    private static final ExecutorService WORKERS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ndjson-work");
        t.setDaemon(true);
        t.setContextClassLoader(NdjsonStream.class.getClassLoader());
        return t;
    });

    private NdjsonStream() {
    }

    static ResponseEntity<StreamingResponseBody> of(HttpServletResponse response, Consumer<Consumer<Map<String, Object>>> body) {
        StreamingResponseBody stream = ignored -> {
            BlockingQueue<String> lines = new LinkedBlockingQueue<>();
            AtomicBoolean gone = new AtomicBoolean();
            WORKERS.execute(() -> {
                try {
                    body.accept(m -> {
                        if (!gone.get()) {
                            lines.add(JSON.writeValueAsString(m) + "\n");
                        }
                    });
                } catch (RuntimeException e) {
                    log.error("스트림 일 실패", e);
                } finally {
                    lines.add(END);
                }
            });
            OutputStream out = response.getOutputStream();
            try {
                for (String line = lines.take(); !line.equals(END); line = lines.take()) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (IOException | InterruptedException e) {
                gone.set(true);
                log.info("보던 화면이 연결을 끊음. 남은 일은 끝까지 하고 저장한다: {}", e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson;charset=UTF-8"))
                .header("X-Accel-Buffering", "no")
                .body(stream);
    }
}
