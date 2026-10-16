package com.example.interview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.interview.ai.AiClient;
import com.example.interview.mapper.QuestionSectionMapper;
import com.example.interview.mapper.QuestionTemplateMapper;
import com.example.interview.resume.PdfTextExtractor;
import com.example.interview.service.ExtractionFlowIntegrationTest;
import com.example.interview.service.ExtractionService;
import com.example.interview.service.PositionService;

/** 실제 내장 Tomcat에서 두 탭과 결과 화면이 렌더링되고, 답변 저장이 되는지 확인한다. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WebPagesSmokeTest {

    @LocalServerPort
    int port;

    @MockitoBean
    AiClient aiClient;
    @MockitoBean
    PdfTextExtractor pdfTextExtractor;

    @Autowired
    ExtractionService extractionService;
    @Autowired
    PositionService positionService;
    @Autowired
    QuestionTemplateMapper templateMapper;
    @Autowired
    QuestionSectionMapper sectionMapper;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void tabsResultAndAnswer() throws Exception {
        HttpResponse<String> home = get("/");
        assertThat(home.statusCode()).isEqualTo(302);

        HttpResponse<String> extract = get("/extract");
        assertThat(extract.statusCode()).isEqualTo(200);
        assertThat(extract.body()).contains("면접 질문 추출하기", "공통 질문 설정", "평가항목 추출", "포지션 설명", "파일 추가",
                "포지션 적합도 검사", "면접 질문 추출").doesNotContain("jsessionid", "type=\"file\" id=\"resume\"");

        HttpResponse<String> templates = get("/templates");
        assertThat(templates.statusCode()).isEqualTo(200);
        assertThat(templates.body()).contains("[출퇴근]", "스펙터 작성 가능하신가요?", "질문 추가", "섹션 추가", "기술 검증")
                .doesNotContain("면접 준비 도우미");
        // 탭 순서: 공통 질문 설정 → 면접 질문 추출하기
        assertThat(templates.body().indexOf("공통 질문 설정")).isLessThan(templates.body().indexOf("면접 질문 추출하기"));

        // 섹션·질문 편집 API
        HttpResponse<String> created = json("POST", "/templates/sections", "{\"name\":\"컬처핏\"}");
        assertThat(created.statusCode()).isEqualTo(200);
        String sectionId = created.body().replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");
        assertThat(json("PATCH", "/templates/sections/" + sectionId, "{\"name\":\"컬처 핏\"}").statusCode()).isEqualTo(204);
        HttpResponse<String> q = json("POST", "/templates/sections/" + sectionId + "/questions", "{\"content\":\"몰입 경험?\"}");
        assertThat(q.statusCode()).isEqualTo(200);
        String qid = q.body().replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");
        assertThat(json("PATCH", "/templates/questions/" + qid, "{\"content\":\"가장 몰입한 경험?\"}").statusCode()).isEqualTo(204);
        assertThat(get("/templates").body()).contains("컬처 핏", "가장 몰입한 경험?");
        HttpResponse<String> badOrder = json("PUT", "/templates/order", "[{\"sectionId\":" + sectionId + ",\"questionIds\":[]}]");
        assertThat(badOrder.statusCode()).isEqualTo(400);
        assertThat(badOrder.body()).contains("새로고침");
        assertThat(json("PATCH", "/templates/sections/" + sectionId, "{\"name\":\"  \"}").statusCode()).isEqualTo(400);
        assertThat(json("DELETE", "/templates/sections/" + sectionId, null).statusCode()).isEqualTo(204);
        assertThat(get("/templates").body()).doesNotContain("컬처 핏");

        ExtractionFlowIntegrationTest.stubAi(aiClient, templateMapper, sectionMapper);
        when(pdfTextExtractor.extract(any())).thenReturn(ExtractionFlowIntegrationTest.RESUME + "<script>alert(1)</script>");
        Long positionId = ExtractionFlowIntegrationTest.startPosition(positionService, "백엔드 개발자");

        // ① 평가항목: 화면 + 편집 API
        String positionPage = get("/positions/" + positionId).body();
        assertThat(positionPage).contains("백엔드 개발자 (Java)", "필수", "우대", "대용량 트래픽 처리 경험", "Kafka 사용 경험",
                "평가항목 추가", "type=\"file\" id=\"resume\"", "적합도 검사");
        HttpResponse<String> crit = json("POST", "/positions/" + positionId + "/criteria",
                "{\"name\":\"Kotlin 경험\",\"requirement\":\"PREFERRED\"}");
        assertThat(crit.statusCode()).isEqualTo(200);
        String critId = crit.body().replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");
        assertThat(json("PATCH", "/positions/" + positionId + "/criteria/" + critId,
                "{\"name\":\"Kotlin 실무 경험\",\"requirement\":\"MUST\"}").statusCode()).isEqualTo(204);
        assertThat(get("/positions/" + positionId).body()).contains("Kotlin 실무 경험");
        assertThat(json("DELETE", "/positions/" + positionId + "/criteria/" + critId, null).statusCode()).isEqualTo(204);
        assertThat(json("PATCH", "/positions/" + (positionId + 999) + "/criteria/" + critId, "{\"name\":\"x\"}").statusCode())
                .isEqualTo(404);
        assertThat(get("/extract").body()).contains("최근 포지션", "백엔드 개발자 (Java)");

        // ② 적합도 검사 → ③ 면접 질문
        Long id = extractionService.uploadResume(positionId, ExtractionFlowIntegrationTest.pdf());
        String checked = get("/results/" + id).body();
        assertThat(checked).contains("홍길동", "직접 근거", "일 100만 건 주문 데이터 배치 처리 개선", "원문에서 찾지 못한",
                "면접 질문 추출</button>", "class=\"print-head\"").doesNotContain("<script>alert(1)</script>", "id=\"iq-board\"");
        extractionService.generateQuestions(id);

        HttpResponse<String> result = get("/results/" + id);
        assertThat(result.statusCode()).isEqualTo(200);
        String body = result.body();
        assertThat(body).contains("100만 건 배치 처리 시간을 어떻게 측정하셨나요?", "병목은 어디였나요?", "답변 기록", "id=\"pdf-btn\"", "면접질문지_홍길동",
                "질문 추가", "경력 검증");

        HttpResponse<String> addedQ = json("POST", "/results/" + id + "/questions", "{\"section\":\"기술 검증\",\"content\":\"추가 질문\"}");
        assertThat(addedQ.statusCode()).isEqualTo(200);
        String addedId = addedQ.body().replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");
        assertThat(json("PATCH", "/results/" + id + "/questions/" + addedId, "{\"content\":\"고친 추가 질문\"}").statusCode()).isEqualTo(204);
        assertThat(get("/results/" + id).body()).contains("고친 추가 질문", "직접 추가");
        assertThat(json("DELETE", "/results/" + id + "/questions/" + addedId, null).statusCode()).isEqualTo(204);
        assertThat(json("PUT", "/results/" + id + "/questions/order", "[{\"section\":\"x\",\"questionIds\":[]}]").statusCode())
                .isEqualTo(400);

        // 답변 자동 저장 (fetch)
        Matcher m = Pattern.compile("data-url=\"(/results/" + id + "/questions/(\\d+)/answer)\"").matcher(body);
        assertThat(m.find()).isTrue();
        HttpResponse<String> saved = post(m.group(1), "answer=" + enc("측정은 nGrinder로 했다고 답함"), true);
        assertThat(saved.statusCode()).isEqualTo(204);
        assertThat(get("/results/" + id).body()).contains("측정은 nGrinder로 했다고 답함");
        assertThat(post("/results/" + id + "/questions/999999/answer", "answer=x", true).statusCode()).isEqualTo(404);

        assertThat(get("/results/999999").statusCode()).isEqualTo(404);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String form, boolean fetch) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8));
        if (fetch) {
            b.header("X-Requested-With", "fetch");
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> json(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("X-Requested-With", "fetch")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
