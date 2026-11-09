package com.example.interview.ai;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.example.interview.ai.dto.ArrangeResult;
import com.example.interview.ai.dto.InterviewRecordResult;
import com.example.interview.ai.dto.PlaceResult;
import com.example.interview.ai.dto.QuestionResult;
import com.example.interview.ai.dto.ReportInfoResult;
import com.example.interview.ai.dto.TimelineResult;
import com.example.interview.config.AiProperties;
import com.example.interview.config.LocalAiProperties;
import com.example.interview.domain.AiPurpose;
import com.example.interview.resume.QuoteVerifier;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 「로컬 AI로 처리」로 보내는 작업을 실제 Ollama에 돌려 보는 평가 (사용자 2026-11-09). 평소 테스트에서는 건너뛴다.
 * LOCAL_EVAL_CASES에 케이스 파일(JSON 한 줄에 하나: id, task, content, runs, params)을 주면 앱과 같은 AiClient·프롬프트·검증으로
 * 돌리고, 결과를 LOCAL_EVAL_OUT(JSON 한 줄에 하나)에 쓴다. 맞았는지는 케이스를 만든 쪽이 결과 파일로 따진다.
 */
@EnabledIfEnvironmentVariable(named = "LOCAL_EVAL_CASES", matches = ".+")
class LocalAiEvalTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Test
    void runCases() throws Exception {
        Path cases = Path.of(System.getenv("LOCAL_EVAL_CASES"));
        Path out = Path.of(System.getenv().getOrDefault("LOCAL_EVAL_OUT", cases + ".out.jsonl"));
        String model = System.getenv().getOrDefault("LOCAL_EVAL_MODEL", "gemma4:e4b");
        String url = System.getenv().getOrDefault("LOCAL_EVAL_URL", "http://localhost:11434");

        LocalAiSwitch on = mock(LocalAiSwitch.class);
        when(on.routesLocal(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        AiProperties props = new AiProperties("", "http://unused", "unused", "unused", 1000, 2000,
                "max_completion_tokens", true, 5, null, null, null, null);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(java.time.Duration.ofMinutes(30));
        RestClient local = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        AiClient client = new AiClient(RestClient.create(), props, local,
                new LocalAiProperties(url, model, 8192, 1800, true, "1h", 32768),
                mock(AiCallLogger.class), new ApiKeyStore(props), on);
        PromptLoader prompts = new PromptLoader();
        QuoteVerifier quotes = new QuoteVerifier();

        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (String line : Files.readAllLines(cases, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode c = JSON.readTree(line);
                int runs = c.path("runs").asInt(1);
                for (int run = 1; run <= runs; run++) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", c.path("id").asString());
                    row.put("task", c.path("task").asString());
                    row.put("run", run);
                    long start = System.currentTimeMillis();
                    try {
                        Object result = run(client, prompts, quotes, c, row);
                        row.put("ok", true);
                        row.put("result", result);
                    } catch (RuntimeException e) {
                        row.put("ok", false);
                        row.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                    row.put("ms", System.currentTimeMillis() - start);
                    w.write(JSON.writeValueAsString(row));
                    w.newLine();
                    w.flush();
                    System.out.println("[local-eval] " + row.get("id") + " #" + run + " ok=" + row.get("ok") + " " + row.get("ms") + "ms");
                }
            }
        }
    }

    private static Object run(AiClient client, PromptLoader prompts, QuoteVerifier quotes, JsonNode c,
            Map<String, Object> row) {
        String content = c.path("content").asString();
        JsonNode p = c.path("params");
        switch (c.path("task").asString()) {
            case "TIMELINE":
                return client.call(AiPurpose.TIMELINE, 0L, prompts.load("timeline"), content, TimelineResult.class,
                        TimelineResult::validate);
            case "ARRANGE": {
                List<Long> templateIds = longs(p.path("template_ids"));
                int n = p.path("question_count").asInt();
                return client.call(AiPurpose.ARRANGE, 0L, prompts.load("arrange"), content, ArrangeResult.class,
                        r -> r.validate(templateIds, n));
            }
            case "QUESTION_PLACE": {
                int sheet = p.path("sheet_size").asInt();
                int n = p.path("question_count").asInt();
                return client.call(AiPurpose.QUESTION_PLACE, 0L, prompts.load("place"), content, PlaceResult.class,
                        r -> r.validate(sheet, n));
            }
            case "QUESTION": {
                Set<String> types = new HashSet<>(strings(p.path("types")));
                Map<Long, Long> refs = new LinkedHashMap<>();
                p.path("claim_refs").properties().forEach(e -> refs.put(Long.parseLong(e.getKey()), e.getValue().asLong()));
                Set<Long> experience = new HashSet<>(longs(p.path("experience_criteria")));
                Set<Long> criteria = new HashSet<>(longs(p.path("criteria_ids")));
                Set<String> customs = new HashSet<>(strings(p.path("custom_codes")));
                QuestionResult r = client.call(AiPurpose.QUESTION, 0L, prompts.load("questions"), content,
                        QuestionResult.class, q -> q.validate(types, refs, experience,
                                Set.of("VERIFY", "DEPTH", "CHOICE"), customs, criteria));
                return r;
            }
            case "REPORT_INFO":
                return client.call(AiPurpose.REPORT_INFO, 0L, prompts.load("report_info"), content,
                        ReportInfoResult.class, ReportInfoResult::validate);
            case "INTERVIEW_RECORD": {
                int[] deltas = { 0 };
                long start = System.currentTimeMillis();
                long[] first = { 0 };
                InterviewRecordResult r = client.streamJson(AiPurpose.INTERVIEW_RECORD, 0L,
                        prompts.load("interview_record"), content, InterviewRecordResult.class,
                        InterviewRecordResult::validate, d -> {
                            if (first[0] == 0) {
                                first[0] = System.currentTimeMillis() - start;
                            }
                            deltas[0]++;
                        });
                int excerpts = 0;
                int verified = 0;
                for (InterviewRecordResult.Item item : r.getItems()) {
                    if (item.getExcerpt() != null && !item.getExcerpt().isBlank()) {
                        excerpts++;
                        boolean all = true;
                        for (String part : item.getExcerpt().split("…|\\.\\.\\.")) {
                            if (!part.isBlank() && !quotes.verify(part, content)) {
                                all = false;
                            }
                        }
                        verified += all ? 1 : 0;
                    }
                }
                row.put("first_delta_ms", first[0]);
                row.put("deltas", deltas[0]);
                row.put("excerpts", excerpts);
                row.put("excerpts_verified", verified);
                return r;
            }
            case "CHAT": {
                long start = System.currentTimeMillis();
                long[] first = { 0 };
                List<Map<String, String>> turns = new ArrayList<>();
                for (JsonNode t : p.path("turns")) {
                    turns.add(Map.of("role", t.path("role").asString(), "content", t.path("content").asString()));
                }
                String reply = client.chatStream(AiPurpose.CHAT, 0L, prompts.load("chat") + "\n\n" + content, turns, d -> {
                    if (first[0] == 0) {
                        first[0] = System.currentTimeMillis() - start;
                    }
                });
                row.put("first_delta_ms", first[0]);
                return reply;
            }
            default:
                throw new IllegalArgumentException("모르는 작업: " + c.path("task").asString());
        }
    }

    private static List<Long> longs(JsonNode array) {
        List<Long> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asLong()));
        return out;
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }
}
