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
import java.util.List;
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
import com.example.interview.service.CandidateService;
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
    CandidateService candidateService;
    @Autowired
    PositionService positionService;
    @Autowired
    QuestionTemplateMapper templateMapper;
    @Autowired
    QuestionSectionMapper sectionMapper;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .cookieHandler(new java.net.CookieManager()).build();

    @Test
    void tabsResultAndAnswer() throws Exception {
        HttpResponse<String> home = get("/");
        assertThat(home.statusCode()).isEqualTo(302);

        assertThat(get("/extract").statusCode()).isEqualTo(302);
        HttpResponse<String> positions = get("/positions");
        assertThat(positions.statusCode()).isEqualTo(200);
        assertThat(positions.body()).contains("공통 질문 설정", "평가 항목 추출", "포지션 적합도 검사", "지원자 비교", "새 포지션", "평가항목 추출<svg", "파일 추가")
                .doesNotContain("jsessionid", "type=\"file\" id=\"resume\"");
        HttpResponse<String> settings = get("/settings");
        assertThat(settings.statusCode()).isEqualTo(200);
        assertThat(settings.body()).contains("GPT API 키", "로컬 AI (Ollama)", "https://ollama.com/download", "ollama pull ");
        // 포지션이 없으면 적합도 검사·비교는 빈 화면
        assertThat(get("/fit").body()).contains("평가항목을 추출한 포지션이 없습니다");
        assertThat(get("/ranking").body()).contains("평가항목을 추출한 포지션이 없습니다");
        // 다른 사이트에서 보낸 POST는 거절한다
        assertThat(http.send(HttpRequest.newBuilder(uri("/positions"))
                .header("Origin", "https://evil.example")
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
                .statusCode()).isEqualTo(403);

        HttpResponse<String> templates = get("/templates");
        assertThat(templates.statusCode()).isEqualTo(200);
        assertThat(templates.body()).contains("[출퇴근]", "스펙터 작성 가능하신가요?", "질문 추가", "섹션 추가", "기술 검증")
                .doesNotContain("면접 준비 도우미");
        // 탭 순서: 공통 질문 설정 → 평가 항목 추출 → 포지션 적합도 검사 → 지원자 비교
        String tb = templates.body();
        assertThat(tb.indexOf("공통 질문 설정")).isLessThan(tb.indexOf("평가 항목 추출"));
        assertThat(tb.indexOf("평가 항목 추출")).isLessThan(tb.indexOf("포지션 적합도 검사"));
        assertThat(tb.indexOf("포지션 적합도 검사")).isLessThan(tb.indexOf("지원자 비교"));

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
        assertThat(positionPage).contains("백엔드 개발자 (Java)", "대용량 트래픽 처리 경험", "Kafka 사용 경험",
                "평가항목 추가", "href=\"/fit?position=" + positionId + "\"", "요구 경력", "경력 3년 이상")
                .doesNotContain("필수", "우대", "type=\"file\" id=\"resume\"", "<dt>연봉</dt>");
        // 적합도 검사: 포지션은 고르기만, 평가항목은 읽기 전용
        String fitPage = get("/fit?position=" + positionId).body();
        assertThat(fitPage).contains("type=\"file\" id=\"resume\"", "data-nav", "selected", "class=\"pick-crit\"", "대용량 트래픽 처리 경험", "포지션 적합도 검사 (1인)")
                .doesNotContain("평가항목 추가", "js-crit-edit");
        assertThat(get("/fit").body()).contains("type=\"file\" id=\"resume\"");
        assertThat(get("/fit?position=" + positionId + "&mode=batch").body()).contains("name=\"applicant-1\"", "name=\"applicant-3\"", "지원자 추가",
                "개인정보 가리기 및 포지션 적합도 검사 일괄 진행", "포지션 적합도 검사 (1인)", "포지션 적합도 검사 (다수)").doesNotContain("id=\"resume\"");
        HttpResponse<String> crit = json("POST", "/positions/" + positionId + "/criteria",
                "{\"name\":\"Kotlin 경험\"}");
        assertThat(crit.statusCode()).isEqualTo(200);
        String critId = crit.body().replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");
        assertThat(json("PATCH", "/positions/" + positionId + "/criteria/" + critId,
                "{\"name\":\"Kotlin 실무 경험\"}").statusCode()).isEqualTo(204);
        assertThat(get("/positions/" + positionId).body()).contains("Kotlin 실무 경험");
        assertThat(json("DELETE", "/positions/" + positionId + "/criteria/" + critId, null).statusCode()).isEqualTo(204);
        assertThat(json("PATCH", "/positions/" + (positionId + 999) + "/criteria/" + critId, "{\"name\":\"x\"}").statusCode())
                .isEqualTo(404);
        assertThat(get("/positions").body()).contains("class=\"side-item \"", "백엔드 개발자 (Java)", "평가항목 3");
        // 경력·학력 조건 칸 (값을 바꾸면 뒤의 점수 확인이 달라지므로 화면과 없는 포지션만 본다)
        assertThat(get("/positions/" + positionId).body()).contains("id=\"conds\"", "data-key=\"requiredEducation\"", "id=\"conds-edit\"", "data-exp-kind=");
        assertThat(json("PATCH", "/positions/" + (positionId + 999) + "/conditions", "{\"requiredExperience\":\"x\"}").statusCode()).isEqualTo(404);
        // 포지션 이름 바꾸기
        HttpResponse<String> renamed = json("PATCH", "/positions/" + positionId, "{\"title\":\"<b>10월 공채</b>\"}");
        assertThat(renamed.statusCode()).isEqualTo(200);
        assertThat(renamed.body()).contains("<b>10월 공채</b>");
        assertThat(get("/positions/" + positionId).body()).contains("id=\"pos-rename\"", "class=\"icon-btn side-rename\"", "&lt;b&gt;10월 공채&lt;/b&gt;").doesNotContain("<b>10월 공채</b>");
        assertThat(json("PATCH", "/positions/" + positionId, "{\"title\":\" \"}").statusCode()).isEqualTo(400);
        assertThat(json("PATCH", "/positions/" + positionId, "{\"title\":\"백엔드 개발자 (Java)\"}").statusCode()).isEqualTo(200);

        // ② 적합도 검사 → ③ 면접 질문
        Long id = extractionService.uploadResume(positionId, List.of(ExtractionFlowIntegrationTest.pdf()));
        String review = get("/results/" + id).body();
        // 가리기 직후에는 가린 본문만, 인적사항·경력·학력·연봉·타임라인은 적합도 검사 뒤에
        assertThat(review).contains("id=\"mask-view\"", "name=\"maskedText\"", "[전화번호]", "[A사] 백엔드팀", "&lt;script&gt;", "적합도 검사</button>", "[교육]")
                .doesNotContain("class=\"profile\"", "가나커머스", "가나정보교육원", "<h3>타임라인</h3>",
                        "<script>alert(1)</script>", "010-1111-2222", "직접 근거");
        assertThat(get("/ranking?position=" + positionId).body()).contains("검사 전", "검토 중", "홍길동").doesNotContain("class=\"rk-no\">1<");
        assertThat(get("/positions/" + positionId + "/candidates").body()).contains("\"name\":\"홍길동\"", "\"label\":\"검토 중\"", "\"inProgress\":false");
        // 지원자 이름 바꾸기 (목록의 연필)
        assertThat(review).contains("class=\"icon-btn side-rename\"", "data-cand-name", "id=\"mask-edit\"");
        HttpResponse<String> renamedCand = json("PATCH", "/results/" + id, "{\"name\":\"  1번 지원자 \"}");
        assertThat(renamedCand.statusCode()).isEqualTo(200);
        assertThat(renamedCand.body()).contains("1번 지원자");
        assertThat(get("/fit?position=" + positionId).body()).contains("1번 지원자");
        assertThat(json("PATCH", "/results/" + id, "{\"name\":\"\"}").statusCode()).isEqualTo(400);
        assertThat(json("PATCH", "/results/" + id, "{\"name\":\"홍길동\"}").statusCode()).isEqualTo(200);
        String reviewed = candidateService.get(id).getMaskedText();
        assertThat(post("/results/" + id + "/check", "maskedText=" + URLEncoder.encode(reviewed, StandardCharsets.UTF_8), false)
                .statusCode()).isEqualTo(302);
        String checked = get("/results/" + id).body();
        // 검사 직후 한 번만 도장 애니메이션, 가린 본문은 결과 위에 그대로
        assertThat(checked).contains("class=\"profile is-stamping\"", "id=\"mask-view\"", "다시 검사</button>").doesNotContain("/review\"");
        assertThat(get("/results/" + id).body()).contains("class=\"profile\"").doesNotContain("is-stamping");
        assertThat(checked).contains("가나커머스", "class=\"gauge\"", "공고 경력 3년 이상",
                "<h3>타임라인</h3>", "is-career", "title=\"가나커머스 · 백엔드팀 · 2021.03 ~ 2024.02\"", "title=\"이력서상 기록 없음 · 2020.07 ~ 2021.02 (8개월)\"")
                .doesNotContain("공고까지", "없는항목", "<h3>연봉", "5,000만원", "<h3>교육</h3>");
        assertThat(checked).contains("홍길동", "직접 근거", "총 2년 11개월", "포지션 관련 2년 6개월", "title=\"백엔드 업무 기간\"",
                "class=\"gauge-total\" style=\"width: 97%\"", "class=\"gauge-fill\" style=\"width: 83%\"", "<span class=\"stamp is-unmet\">미충족</span>","일 100만 건 주문 데이터 배치 처리 개선", "원문에서 찾지 못한",
                "질문 추출</button>", "name=\"types\" value=\"EXPERIENCE\" checked", "class=\"print-head\"").doesNotContain("<script>alert(1)</script>", "id=\"iq-board\"");
        // 점수: 경력 0(관련 30개월 < 36개월) + 학력 1(공고 조건 없음) + 근거 직접 1·없음 0·없음 0 = 2 / 5
        assertThat(checked).contains("class=\"score-pill\"", "2<small> / 5점</small>", "side-score\">2<small> / 5</small>");
        String ranking = get("/ranking?position=" + positionId).body();
        assertThat(ranking).contains("class=\"rk-no\">1<", "href=\"/results/" + id + "\"", "rk-score\">2<small> / 5</small>", "rk-sq ev-NONE\"", "rk-sq ev-DIRECT\"",
                        "rk-cond is-unmet\">미충족<", "rk-cond is-met\">충족<")
                .doesNotContain("검사 전", ">연봉</th>", "제외됨");
        // 경력 미충족 제외를 켜면 순위 없이 제외됨으로
        assertThat(json("PATCH", "/positions/" + positionId + "/exclusions", "{\"career\":true,\"education\":false}").statusCode()).isEqualTo(204);
        assertThat(get("/ranking?position=" + positionId).body()).contains("제외됨 1명", "rk-why\">경력 미충족<", "checked><span class=\"switch-track\"></span>경력 미충족 제외")
                .doesNotContain("class=\"rk-no\">1<");
        assertThat(json("PATCH", "/positions/" + positionId + "/exclusions", "{\"career\":false,\"education\":false}").statusCode()).isEqualTo(204);
        extractionService.extractQuestions(id, com.example.interview.domain.QuestionType.SELECTABLE);
        assertThat(get("/results/" + id).body()).contains("추출한 질문 <b>1</b>", "공통 질문과 합치기", "거짓말 판독기").doesNotContain("id=\"pdf-btn\"");
        extractionService.mergeQuestions(id);

        HttpResponse<String> result = get("/results/" + id);
        assertThat(result.statusCode()).isEqualTo(200);
        String body = result.body();
        assertThat(body).contains("100만 건 배치 처리 시간을 어떻게 측정하셨나요?", "병목은 어디였나요?", "답변 기록", "id=\"pdf-btn\"", "면접질문지_홍길동",
                "질문 추가", "kind kind-verify\">거짓말 판독기<", "AI 의견 참고", "AI 의견 받기</button>", "data-open=\"aa-panel\">자동 답변 채우기<", "name=\"transcript\"").doesNotContain("추출한 질문 <b>");

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

        // 답변을 적으면 체크되고, 번호를 눌러 풀거나 다시 체크한다
        String answeredId = m.group(2);
        String checkedRow = "is-checked\" data-id=\"" + answeredId + "\"";
        assertThat(get("/results/" + id).body()).as("답변을 적으면 체크").contains(checkedRow);
        assertThat(post("/results/" + id + "/questions/" + answeredId + "/checked", "checked=false", true).statusCode()).isEqualTo(204);
        assertThat(get("/results/" + id).body()).doesNotContain(checkedRow);
        assertThat(post("/results/" + id + "/questions/" + answeredId + "/checked", "checked=true", true).statusCode()).isEqualTo(204);
        assertThat(get("/results/" + id).body()).contains(checkedRow, "aria-pressed=\"true\"");
        assertThat(post("/results/" + id + "/questions/999999/checked", "checked=true", true).statusCode()).isEqualTo(404);

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
