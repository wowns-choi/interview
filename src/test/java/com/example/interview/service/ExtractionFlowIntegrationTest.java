package com.example.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.interview.ai.AiCallException;
import com.example.interview.ai.AiClient;
import com.example.interview.ai.dto.ArrangeResult;
import com.example.interview.ai.dto.CriteriaResult;
import com.example.interview.ai.dto.EvidenceResult;
import com.example.interview.ai.dto.QuestionResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateStatus;
import com.example.interview.domain.EvidenceType;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.QuestionOrigin;
import com.example.interview.domain.QuestionTemplate;
import com.example.interview.mapper.QuestionSectionMapper;
import com.example.interview.mapper.QuestionTemplateMapper;
import com.example.interview.resume.PdfTextExtractor;
import com.example.interview.resume.ResumeProcessingException;
import com.example.interview.web.dto.QuestionOrder;
import com.example.interview.web.dto.QuestionView;
import com.example.interview.web.dto.ResultReport;

/** AI만 가짜로 바꾸고 "포지션 + 이력서 → 결과 → 답변 → PDF" 흐름을 실제 DB(H2)로 확인한다. */
@SpringBootTest
public class ExtractionFlowIntegrationTest {

    public static final String RESUME = """
            이력서
            홍길동
            연락처 010-1111-2222
            - 일 100만 건 주문 데이터 배치 처리 개선
            - Redis 캐시 도입으로 응답속도 40% 개선
            """;

    @MockitoBean
    AiClient aiClient;
    @MockitoBean
    PdfTextExtractor pdfTextExtractor;

    @Autowired
    ExtractionService extractionService;
    @Autowired
    ReportService reportService;
    @Autowired
    CandidateService candidateService;
    @Autowired
    PositionService positionService;
    @Autowired
    QuestionTemplateMapper templateMapper;
    @Autowired
    QuestionSectionMapper sectionMapper;

    @Test
    void extractsWholeSheetFromPositionAndResume() {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);

        Long positionId = startPosition(positionService, "백엔드 개발자 채용\n자격요건: Java");
        Long id = extractionService.uploadResume(positionId, pdf());

        Candidate candidate = candidateService.get(id);
        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.ANALYZED);
        ResultReport checked = reportService.report(id);
        assertThat(checked.isFitChecked()).isTrue();
        assertThat(checked.getSections()).as("질문은 버튼을 눌러야 만든다").isEmpty();

        extractionService.generateQuestions(id);
        candidate = candidateService.get(id);
        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.DONE);
        assertThat(candidate.getDisplayName()).isEqualTo("홍길동");
        assertThat(candidate.getMaskedText()).doesNotContain("홍길동", "010-1111-2222").contains("[이름]", "[전화번호]");
        assertThat(positionService.get(candidate.getPositionId()).getTitle()).isEqualTo("백엔드 개발자 (Java)");

        ResultReport r = reportService.report(id);
        assertThat(r.getCriteria()).hasSize(3);
        // MUST 항목이 먼저
        assertThat(r.getCriteria().get(0).getCriterion().getName()).isEqualTo("대용량 트래픽 처리 경험");
        assertThat(r.getCriteria().get(0).getMatch()).isEqualTo(EvidenceType.DIRECT);
        assertThat(r.getNoneCount()).isEqualTo(2);
        assertThat(r.getUnverifiedCount()).isEqualTo(1);

        List<InterviewQuestion> all = r.getSections().stream().flatMap(s -> s.getQuestions().stream())
                .map(QuestionView::getQuestion).toList();
        assertThat(all).filteredOn(q -> q.getOrigin() == QuestionOrigin.CUSTOM).hasSize(1);
        assertThat(all).filteredOn(q -> q.getOrigin() == QuestionOrigin.COMMON_PERSONALIZED
                || q.getOrigin() == QuestionOrigin.COMMON_ORIGINAL).hasSize(24);
        assertThat(r.getQuestionCount()).isEqualTo(24 + 1);
        assertThat(r.getSections().get(0).getSection()).isEqualTo("오프닝");
        assertThat(r.getSections().get(1).getQuestions().get(0).getQuestion().getOrigin())
                .isEqualTo(QuestionOrigin.COMMON_PERSONALIZED);
        assertThat(r.getSections()).extracting(s -> s.getSection()).contains("기술 검증");

        // 답변 기록
        InterviewQuestion custom = all.stream().filter(q -> q.getOrigin() == QuestionOrigin.CUSTOM).findFirst().orElseThrow();
        extractionService.saveAnswer(id, custom.getId(), "nGrinder로 p95를 측정했다고 답함");
        assertThat(reportService.report(id).getAnsweredCount()).isEqualTo(1);
        extractionService.saveAnswer(id, custom.getId(), "  ");
        assertThat(reportService.report(id).getAnsweredCount()).isZero();
        assertThatThrownBy(() -> extractionService.saveAnswer(id + 999, custom.getId(), "x"))
                .isInstanceOf(NotFoundException.class);
        extractionService.saveAnswer(id, custom.getId(), "nGrinder로 p95를 측정했다고 답함");

        // 질문지 편집: 수정 · 추가 · 삭제 · 순서
        InterviewQuestion first = all.get(0);
        extractionService.updateQuestion(id, first.getId(), "수정한 첫 질문");
        Long added = extractionService.addQuestion(id, "기술 검증", "직접 넣은 질문");
        extractionService.deleteQuestion(id, custom.getId());
        assertThatThrownBy(() -> extractionService.deleteQuestion(id + 999, first.getId()))
                .isInstanceOf(NotFoundException.class);

        List<QuestionView> sheet = reportService.report(id).getSections().stream()
                .flatMap(s -> s.getQuestions().stream()).toList();
        assertThat(sheet.get(0).getQuestion().getContent()).isEqualTo("수정한 첫 질문");
        assertThat(sheet).extracting(v -> v.getQuestion().getContent()).contains("직접 넣은 질문")
                .doesNotContain(custom.getContent());

        List<Long> ids = sheet.stream().map(v -> v.getQuestion().getId()).toList();
        List<Long> moved = new java.util.ArrayList<>(ids.subList(1, ids.size()));
        moved.add(ids.get(0));
        extractionService.reorderQuestions(id, List.of(new QuestionOrder("마무리", moved)));
        ResultReport reordered = reportService.report(id);
        assertThat(reordered.getSections()).singleElement().extracting(s -> s.getSection()).isEqualTo("마무리");
        assertThat(reordered.getSections().get(0).getQuestions()).last()
                .extracting(v -> v.getQuestion().getContent()).isEqualTo("수정한 첫 질문");
        assertThatThrownBy(() -> extractionService.reorderQuestions(id, List.of(new QuestionOrder("마무리", List.of(added)))))
                .isInstanceOf(BusinessException.class);

        // 평가항목 편집: 검사 뒤 추가한 항목은 "검사 전"
        positionService.addCriterion(positionId, "Kotlin 경험", null, "PREFERRED");
        assertThat(reportService.report(id).isHasUncheckedCriteria()).isTrue();

        // 이력서 삭제: 포지션은 남는다
        extractionService.deleteCandidate(id);
        assertThatThrownBy(() -> candidateService.get(id)).isInstanceOf(NotFoundException.class);
        assertThat(positionService.criteria(positionId)).hasSize(4);
    }

    @Test
    void aiFailureKeepsResultAsFailedAndRetryRecovers() {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(aiClient.call(eq(AiPurpose.EVIDENCE), anyLong(), anyString(), anyString(), eq(EvidenceResult.class), any()))
                .thenThrow(new AiCallException("일시적 오류"));
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);

        Long id = extractionService.uploadResume(startPosition(positionService, "백엔드 개발자"), pdf());
        Candidate failed = candidateService.get(id);
        assertThat(failed.getStatus()).isEqualTo(CandidateStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("일시적 오류");
        assertThat(reportService.report(id).isFitChecked()).isFalse();

        reset(aiClient);
        stubAi(aiClient, templateMapper, sectionMapper);
        when(aiClient.call(eq(AiPurpose.QUESTION), anyLong(), anyString(), anyString(), eq(QuestionResult.class), any()))
                .thenThrow(new AiCallException("질문 오류"));
        extractionService.checkFit(id);
        assertThat(candidateService.get(id).getStatus()).isEqualTo(CandidateStatus.ANALYZED);
        extractionService.generateQuestions(id);
        assertThat(candidateService.get(id).getStatus()).isEqualTo(CandidateStatus.FAILED);
        assertThat(reportService.report(id).isFitChecked()).as("질문 단계 실패는 근거를 지우지 않음").isTrue();

        reset(aiClient);
        stubAi(aiClient, templateMapper, sectionMapper);
        extractionService.generateQuestions(id);
        assertThat(candidateService.get(id).getStatus()).isEqualTo(CandidateStatus.DONE);
    }

    @Test
    void rejectsBadInputBeforeSavingAnything() {
        stubAi(aiClient, templateMapper, sectionMapper);
        Long positionId = startPosition(positionService, "포지션");
        MockMultipartFile notPdf = new MockMultipartFile("resume", "resume.docx", "application/octet-stream", new byte[] { 1 });
        assertThatThrownBy(() -> extractionService.uploadResume(positionId, notPdf)).isInstanceOf(ResumeProcessingException.class);
        assertThatThrownBy(() -> extractionService.uploadResume(positionId, null)).isInstanceOf(ResumeProcessingException.class);
        assertThat(extractionService.candidates(positionId)).isEmpty();

        Long empty = positionService.create("기준 없는 포지션");
        assertThatThrownBy(() -> extractionService.uploadResume(empty, pdf())).isInstanceOf(BusinessException.class);
    }

    public static Long startPosition(PositionService positionService, String jdText) {
        Long id = positionService.create(jdText);
        positionService.extractCriteria(id);
        return id;
    }

    public static MockMultipartFile pdf() {
        return new MockMultipartFile("resume", "resume.pdf", "application/pdf", new byte[] { 1, 2, 3 });
    }

    /** 평가항목 3개(MUST 2, PREFERRED 1), 근거(직접 1·지어낸 간접 1·없음 2), 맞춤 질문 1개 */
    public static void stubAi(AiClient aiClient, QuestionTemplateMapper templateMapper,
            QuestionSectionMapper sectionMapper) {
        CriteriaResult criteria = new CriteriaResult();
        criteria.setPositionTitle("백엔드 개발자 (Java)");
        criteria.setCriteria(List.of(
                criterion("Kafka 사용 경험", "PREFERRED", "TECH", true),
                criterion("대용량 트래픽 처리 경험", "MUST", "TECH", true),
                criterion("책임감", "MUST", "ETC", false)));
        when(aiClient.call(eq(AiPurpose.CRITERIA_EXTRACT), anyLong(), anyString(), anyString(), anyList(),
                eq(CriteriaResult.class), any())).thenReturn(criteria);

        when(aiClient.call(eq(AiPurpose.EVIDENCE), anyLong(), anyString(), anyString(),
                eq(EvidenceResult.class), any())).thenAnswer(inv -> {
                    String content = inv.getArgument(3);
                    // 실제 평가항목 id는 저장 후에 정해지므로 요청 본문에서 읽는다
                    List<Long> ids = java.util.regex.Pattern.compile("\"criterion_id\"\\s*:\\s*(\\d+)").matcher(content)
                            .results().map(m -> Long.valueOf(m.group(1))).toList();
                    EvidenceResult er = new EvidenceResult();
                    er.setResults(List.of(
                            result(ids.get(0), item("DIRECT", "일 100만 건 주문 데이터 배치 처리 개선"), item("INDIRECT", "지어낸 문장")),
                            result(ids.get(1), item("NONE", null)),
                            result(ids.get(2), item("NONE", null))));
                    return er;
                });

        QuestionResult qr = new QuestionResult();
        QuestionResult.Item custom = new QuestionResult.Item();
        custom.setQuestionType("VERIFY");
        custom.setContent("100만 건 배치 처리 시간을 어떻게 측정하셨나요?");
        custom.setIntent("측정 근거 확인");
        custom.setFollowUps(List.of("병목은 어디였나요?"));
        custom.setEvidenceId(1L);
        custom.setPriority(1);
        qr.setQuestions(List.of(custom));
        when(aiClient.call(eq(AiPurpose.QUESTION), anyLong(), anyString(), anyString(),
                eq(QuestionResult.class), any())).thenReturn(qr);

        // 배치: 공통 질문은 설정 순서대로, PM 질문만 개인화, 추출 질문은 "기술 검증" 끝에
        when(aiClient.call(eq(AiPurpose.ARRANGE), anyLong(), anyString(), anyString(),
                eq(ArrangeResult.class), any())).thenAnswer(inv -> {
                    List<QuestionTemplate> commons = templateMapper.findActive();
                    ArrangeResult ar = new ArrangeResult();
                    List<ArrangeResult.Section> sections = new java.util.ArrayList<>();
                    for (var s : sectionMapper.findAll()) {
                        List<ArrangeResult.Item> items = new java.util.ArrayList<>();
                        for (QuestionTemplate t : commons) {
                            if (t.getSectionId().equals(s.getId())) {
                                ArrangeResult.Item i = new ArrangeResult.Item();
                                i.setTemplateId(t.getId());
                                if (t.getContent().startsWith("PM")) {
                                    i.setPersonalized(true);
                                    i.setContent("배치 개선 프로젝트에서 일정 관리를 맡으셨나요?");
                                    i.setEvidenceId(1L);
                                }
                                items.add(i);
                            }
                        }
                        if (s.getName().equals("기술 검증")) {
                            ArrangeResult.Item q = new ArrangeResult.Item();
                            q.setQuestionId(1L);
                            items.add(q);
                        }
                        if (!items.isEmpty()) {
                            ArrangeResult.Section section = new ArrangeResult.Section();
                            section.setName(s.getName());
                            section.setItems(items);
                            sections.add(section);
                        }
                    }
                    ar.setSections(sections);
                    return ar;
                });
    }

    private static CriteriaResult.Item criterion(String name, String req, String cat, boolean assessable) {
        CriteriaResult.Item i = new CriteriaResult.Item();
        i.setName(name);
        i.setDescription(name + " 설명");
        i.setRequirement(req);
        i.setCategory(cat);
        i.setResumeAssessable(assessable);
        return i;
    }

    private static EvidenceResult.CriterionResult result(Long id, EvidenceResult.Item... items) {
        EvidenceResult.CriterionResult r = new EvidenceResult.CriterionResult();
        r.setCriterionId(id);
        r.setEvidences(List.of(items));
        r.setInterviewPoint("확인 포인트");
        return r;
    }

    private static EvidenceResult.Item item(String type, String quote) {
        EvidenceResult.Item i = new EvidenceResult.Item();
        i.setType(type);
        i.setQuote(quote);
        i.setInterpretation("해석");
        return i;
    }
}
