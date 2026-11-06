package com.example.interview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.interview.domain.InterviewRecord;
import com.example.interview.domain.QaItem;
import com.example.interview.service.InterviewRecordService;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
/** 스트림 응답이 항목마다 바로 나가는지 (Spring이 넘기는 스트림은 flush를 무시해 8KB씩 몰려 나갔다, 2026-11-06) */
class RecordStreamTest {

    @LocalServerPort
    int port;

    @MockitoBean
    InterviewRecordService recordService;

    @Test
    void keepsWorkingAfterTheViewerLeaves() throws Exception {
        java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        when(recordService.prepare(any(), any(), any())).thenReturn(new InterviewRecordService.Prepared(1L, "t"));
        when(recordService.createStreaming(any(), any())).thenAnswer(inv -> {
            Consumer<QaItem> on = inv.getArgument(1);
            List<QaItem> all = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                QaItem q = QaItem.of("INTERVIEWER", "질문 " + i + " " + "가".repeat(400), "답", "");
                all.add(q);
                on.accept(q); // 연결이 끊긴 뒤에도 던지지 않아야 끝까지 간다
                Thread.sleep(100);
            }
            InterviewRecord r = new InterviewRecord();
            r.setItems(all);
            finished.countDown();
            return r;
        });
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/results/1/record/stream"))
                .header("Content-Type", "application/x-www-form-urlencoded").header("X-Requested-With", "fetch")
                .POST(HttpRequest.BodyPublishers.ofString("transcript=x")).build();
        HttpResponse<InputStream> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofInputStream());
        InputStream in = res.body();
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).readLine();
        in.close(); // 페이지를 떠남
        assertThat(finished.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void itemsArriveAsTheyAreMade() throws Exception {
        when(recordService.prepare(any(), any(), any())).thenReturn(new InterviewRecordService.Prepared(1L, "t"));
        when(recordService.createStreaming(any(), any())).thenAnswer(inv -> {
            Consumer<QaItem> on = inv.getArgument(1);
            List<QaItem> all = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                QaItem q = QaItem.of("INTERVIEWER", "질문 " + i + " 지금까지 해 온 프로젝트 중 가장 어려웠던 문제는 무엇이었나요?",
                        "대용량 배치 처리에서 성능 문제를 겪었고 쿼리를 묶어 실행해 처리 시간을 크게 줄였다고 답함.", "그때 배치가 너무 느려서요 묶어서 넣었더니 빨라졌습니다");
                all.add(q);
                on.accept(q);
                Thread.sleep(300);
            }
            InterviewRecord r = new InterviewRecord();
            r.setItems(all);
            return r;
        });
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/results/1/record/stream"))
                .header("Content-Type", "application/x-www-form-urlencoded").header("X-Requested-With", "fetch")
                .POST(HttpRequest.BodyPublishers.ofString("transcript=x")).build();
        long t0 = System.currentTimeMillis();
        HttpResponse<InputStream> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofInputStream());
        List<Long> itemAt = new ArrayList<>();
        long endAt = -1;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                long t = System.currentTimeMillis() - t0;
                if (line.startsWith("{\"item\"")) {
                    itemAt.add(t);
                } else {
                    endAt = t;
                }
            }
        }
        assertThat(itemAt).hasSize(20);
        // 첫 항목은 끝나기 한참 전에 와야 한다 (20개 × 0.3초)
        assertThat(endAt - itemAt.get(0)).isGreaterThan(4000);
    }
}
