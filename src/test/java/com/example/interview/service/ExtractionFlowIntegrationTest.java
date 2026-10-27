package com.example.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.interview.ai.AiCallException;
import com.example.interview.ai.AiClient;
import com.example.interview.ai.dto.ArrangeResult;
import com.example.interview.ai.dto.InterviewRecordResult;
import com.example.interview.ai.dto.ReportInfoResult;
import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.ai.dto.CareerMeetsResult;
import com.example.interview.ai.dto.CriteriaResult;
import com.example.interview.ai.dto.EvidenceResult;
import com.example.interview.ai.dto.OpinionResult;
import com.example.interview.ai.dto.PiiExtract;
import com.example.interview.ai.dto.PiiExtracts;
import com.example.interview.ai.dto.PiiResult;
import com.example.interview.ai.dto.QuestionResult;
import com.example.interview.ai.dto.TimelineResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateProfile;
import com.example.interview.domain.CandidateStatus;
import com.example.interview.domain.EvidenceType;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.InterviewRecord;
import com.example.interview.domain.InterviewReport;
import com.example.interview.domain.QaItem;
import com.example.interview.domain.QuestionOrigin;
import com.example.interview.domain.QuestionTemplate;
import com.example.interview.domain.QuestionType;
import com.example.interview.mapper.QuestionSectionMapper;
import com.example.interview.mapper.QuestionTemplateMapper;
import com.example.interview.resume.PdfTextExtractor;
import com.example.interview.resume.ResumeProcessingException;
import com.example.interview.web.dto.QuestionOrder;
import com.example.interview.web.dto.QuestionView;
import com.example.interview.web.dto.ResultReport;
import com.example.interview.web.dto.SectionView;

/** AI만 가짜로 바꾸고 "포지션 + 이력서 → 결과 → 답변 → PDF" 흐름을 실제 DB(H2)로 확인한다. */
@SpringBootTest
public class ExtractionFlowIntegrationTest {

    public static final String RESUME = """
            이력서
            홍길동
            연락처 010-1111-2222
            - 일 100만 건 주문 데이터 배치 처리 개선
            - Redis 캐시 도입으로 응답속도 40% 개선
            가나커머스 백엔드팀 2021.03 ~ 2024.02 (2년 11개월)
            희망 연봉 5,000만원
            2024.01 ~ 2024.06 가나정보교육원 국비 과정 수료
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
    OpinionService opinionService;
    @Autowired
    InterviewRecordService interviewRecordService;
    @Autowired
    InterviewReportService interviewReportService;
    @Autowired
    RankingService rankingService;
    @Autowired
    BatchCheckService batchCheckService;
    @Autowired
    PrivacyModeService privacyModeService;
    @Autowired
    CandidateService candidateService;
    @Autowired
    PositionService positionService;
    @Autowired
    QuestionTemplateMapper templateMapper;
    @Autowired
    TemplateService templateService;
    @Autowired
    QuestionSectionMapper sectionMapper;

    @Test
    void extractsWholeSheetFromPositionAndResume() {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);

        Long positionId = startPosition(positionService, "백엔드 개발자 채용\n자격요건: Java");
        Long id = extractionService.uploadResume(positionId, List.of(pdf()));
        assertThat(candidateService.get(id).getStatus()).as("업로드는 가리기까지만").isEqualTo(CandidateStatus.UPLOADED);
        assertThat(reportService.report(id).isFitChecked()).isFalse();
        // 학력·경력: 원문에 있는 값만 화면용으로 남기고, 이력서 글에서는 회사·연봉을 가린다. 연봉은 가리기만 하고 저장하지 않는다
        CandidateProfile profile = reportService.report(id).getProfile();
        assertThat(profile.getCareers()).extracting("company", "period").containsExactly(tuple("가나커머스", "2021.03 ~ 2024.02"));
        assertThat(profile.getEducation()).as("원문에 없는 학교는 버린다").isEmpty();
        assertThat(profile.getDesiredSalary()).isEmpty();
        assertThat(candidateService.get(id).getMaskedText()).contains("[A사] 백엔드팀", "희망 연봉 [연봉]")
                .doesNotContain("가나커머스", "5,000만원");
        // 회사 이름표는 업로드 때 정해 프로필에 적고, 외부 AI는 검사 때까지 부르지 않는다
        assertThat(profile.getCareers()).extracting("alias").containsExactly("A사");
        assertThat(reportService.report(id).getCareerEval()).isNull();
        verify(aiClient, never()).call(eq(AiPurpose.TIMELINE), anyLong(), anyString(), anyString(),
                eq(TimelineResult.class), any());

        // 검토: 놓친 단어를 직접 가린 글로 검사
        String reviewed = candidateService.get(id).getMaskedText().replace("Redis", "[개인정보]");
        extractionService.checkFit(id, reviewed);

        Candidate candidate = candidateService.get(id);
        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.ANALYZED);
        assertThat(candidate.getMaskedText()).contains("[개인정보] 캐시 도입").doesNotContain("Redis");
        ResultReport checked = reportService.report(id);
        assertThat(checked.isFitChecked()).isTrue();
        assertThat(checked.getSections()).as("질문은 버튼을 눌러야 만든다").isEmpty();
        // 총·관련 경력은 근거 분석 호출에서, 타임라인은 학력 호출에서
        assertThat(checked.getCareerEval()).extracting("totalMonths", "relevantMonths", "educationMeets")
                .containsExactly(35, 30, "UNKNOWN");
        assertThat(checked.getCareerEval().getCertificates()).as("자격증은 근거 분석 호출에서")
                .extracting("infoEngineer", "sqld").containsExactly(true, false);
        assertThat(checked.getTimeline()).isNotNull();

        // 질문 추출은 "추출한 질문"에 쌓이기만 하고, 합치기를 눌러야 질문지가 된다
        assertThat(extractionService.extractQuestions(id, QuestionType.SELECTABLE).count()).isEqualTo(1);
        ResultReport pooled = reportService.report(id);
        assertThat(pooled.getSections()).isEmpty();
        assertThat(pooled.getExtracted()).extracting(SectionView::getSection).containsExactly("거짓말 판독기");
        assertThat(candidateService.get(id).getStatus()).isEqualTo(CandidateStatus.ANALYZED);
        // 공통 질문 설정의 필수 표시는 질문지에 복사된다
        Long mustTemplate = templateMapper.findActive().get(0).getId();
        templateMapper.updateMustAsk(mustTemplate, true);
        try {
            extractionService.mergeQuestions(id);
        } finally {
            templateMapper.updateMustAsk(mustTemplate, false);
        }
        assertThat(reportService.report(id).getExtracted()).isEmpty();
        candidate = candidateService.get(id);
        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.DONE);
        assertThat(candidate.getDisplayName()).isEqualTo("홍길동");
        assertThat(candidate.getMaskedText()).doesNotContain("홍길동", "010-1111-2222").contains("[이름]", "[전화번호]");
        assertThat(positionService.get(candidate.getPositionId()).getTitle()).isEqualTo("백엔드 개발자 (Java)");

        ResultReport r = reportService.report(id);
        assertThat(r.getCriteria()).hasSize(3);
        assertThat(r.getCriteria().get(0).getCriterion().getName()).isEqualTo("대용량 트래픽 처리 경험");
        assertThat(r.getCriteria().get(0).getMatch()).isEqualTo(EvidenceType.DIRECT);
        assertThat(r.getNoneCount()).isEqualTo(2);
        assertThat(r.getUnverifiedCount()).isEqualTo(1);

        List<InterviewQuestion> all = r.getSections().stream().flatMap(s -> s.getQuestions().stream())
                .map(QuestionView::getQuestion).toList();
        assertThat(all).filteredOn(InterviewQuestion::isMustAsk).extracting(InterviewQuestion::getTemplateId)
                .containsExactly(mustTemplate);
        // 질문지에서도 필수로 정하거나 풀 수 있다
        InterviewQuestion firstCustom = all.stream().filter(q -> q.getOrigin() == QuestionOrigin.CUSTOM).findFirst().orElseThrow();
        extractionService.markMustAsk(id, firstCustom.getId(), true);
        assertThat(reportService.report(id).getSections().stream().flatMap(s -> s.getQuestions().stream())
                .map(QuestionView::getQuestion).filter(InterviewQuestion::isMustAsk)).hasSize(2);
        extractionService.markMustAsk(id, firstCustom.getId(), false);
        // 설정에서 나중에 바꿔도 이미 만든 질문지에 반영된다
        templateService.setMustAsk(mustTemplate, false);
        assertThat(reportService.report(id).getSections().stream().flatMap(s -> s.getQuestions().stream())
                .map(QuestionView::getQuestion).filter(InterviewQuestion::isMustAsk)).isEmpty();
        assertThatThrownBy(() -> extractionService.markMustAsk(id + 999, firstCustom.getId(), true))
                .isInstanceOf(NotFoundException.class);
        assertThat(all).filteredOn(q -> q.getOrigin() == QuestionOrigin.CUSTOM).hasSize(1);
        assertThat(all).filteredOn(q -> q.getOrigin() == QuestionOrigin.COMMON_ORIGINAL).hasSize(24);
        assertThat(r.getQuestionCount()).isEqualTo(24 + 1);
        assertThat(r.getSections().get(0).getSection()).isEqualTo("오프닝");
        assertThat(all).as("공통 질문은 고치지 않는다").noneMatch(q -> q.getOrigin() == QuestionOrigin.COMMON_PERSONALIZED);
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

        // 합친 뒤 더 추출해 넣으면 기존 질문지는 그대로, 새 질문만 끼워 넣는다 (배치 AI는 다시 부르지 않음)
        extractionService.extractQuestions(id, List.of(QuestionType.VERIFY));
        extractionService.reorderQuestions(id, List.of(new QuestionOrder("마무리", moved)));
        extractionService.mergeQuestions(id);
        ResultReport inserted = reportService.report(id);
        assertThat(inserted.getSections()).extracting(s -> s.getSection()).containsExactly("마무리", "추가 질문");
        assertThat(inserted.getSections().get(0).getQuestions()).hasSize(moved.size());
        verify(aiClient, org.mockito.Mockito.times(1)).call(eq(AiPurpose.ARRANGE), anyLong(), anyString(), anyString(), any(), any());
        assertThatThrownBy(() -> extractionService.mergeQuestions(id)).isInstanceOf(BusinessException.class);

        // 평가항목 편집: 검사 뒤 추가한 항목은 "검사 전"
        assertThat(rankingService.rank(positionId)).extracting("rank", "state").containsExactly(org.assertj.core.groups.Tuple.tuple(1, "RANKED"));
        positionService.addCriterion(positionId, "Kotlin 경험", null);
        assertThat(reportService.report(id).isHasUncheckedCriteria()).isTrue();
        // 검사 뒤 평가항목이 바뀌면 순위에서 빠진다 (재검사 필요)
        assertThat(rankingService.rank(positionId)).extracting("rank", "state").containsExactly(org.assertj.core.groups.Tuple.tuple(null, "STALE"));

        // 면접 기록: 날짜나 합격을 고르면 면접 본 것으로 저장하고, 지원자 비교에서도 보인다
        candidateService.updateInterview(id, false, java.time.LocalDate.of(2026, 10, 22), "PASS");
        assertThat(candidateService.get(id)).extracting("interviewed", "interviewDate", "decision")
                .containsExactly(true, java.time.LocalDate.of(2026, 10, 22), "PASS");
        assertThat(rankingService.rank(positionId)).extracting("interviewed", "decision")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(true, "PASS"));
        assertThatThrownBy(() -> candidateService.updateInterview(id, true, null, "보류"))
                .isInstanceOf(BusinessException.class);
        candidateService.updateInterview(id, false, null, "");
        assertThat(candidateService.get(id)).extracting("interviewed", "interviewDate", "decision")
                .containsExactly(false, null, null);

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

        Long id = extractionService.uploadResume(startPosition(positionService, "백엔드 개발자"), List.of(pdf()));
        extractionService.checkFit(id, null);
        Candidate failed = candidateService.get(id);
        assertThat(failed.getStatus()).isEqualTo(CandidateStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("일시적 오류");
        assertThat(reportService.report(id).getCareerEval()).as("학력 호출은 근거 분석이 실패해도 저장")
                .extracting("totalMonths", "educationMeets").containsExactly(null, "UNKNOWN");
        assertThat(reportService.report(id).isFitChecked()).isFalse();

        reset(aiClient);
        stubAi(aiClient, templateMapper, sectionMapper);
        when(aiClient.call(eq(AiPurpose.QUESTION), anyLong(), anyString(), anyString(), eq(QuestionResult.class), any()))
                .thenThrow(new AiCallException("질문 오류"));
        extractionService.checkFit(id);
        assertThat(candidateService.get(id).getStatus()).isEqualTo(CandidateStatus.ANALYZED);
        assertThatThrownBy(() -> extractionService.extractQuestions(id, QuestionType.SELECTABLE))
                .isInstanceOf(BusinessException.class).hasMessageContaining("질문 오류");
        assertThat(candidateService.get(id).getStatus()).as("질문 추출 실패는 지원자 상태를 바꾸지 않음").isEqualTo(CandidateStatus.ANALYZED);
        assertThat(reportService.report(id).isFitChecked()).as("질문 단계 실패는 근거를 지우지 않음").isTrue();

        reset(aiClient);
        stubAi(aiClient, templateMapper, sectionMapper);
        extractionService.extractQuestions(id, QuestionType.SELECTABLE);
        extractionService.mergeQuestions(id);
        assertThat(candidateService.get(id).getStatus()).isEqualTo(CandidateStatus.DONE);
    }

    @Test
    void customQuestionTypesGoToAiAsCodesAndComeBackUnderTheirOwnNames() {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);
        Long id = extractionService.uploadResume(startPosition(positionService, "백엔드 개발자"), List.of(pdf()));
        extractionService.checkFit(id, null);

        QuestionResult qr = new QuestionResult();
        QuestionResult.Item mine = new QuestionResult.Item();
        mine.setQuestionType("CUSTOM_1");
        mine.setContent("팀원과 의견이 갈렸을 때 어떻게 정리하셨나요?");
        mine.setCriterionId(999_999L); // 없는 평가항목은 지운다
        QuestionResult.Item verify = new QuestionResult.Item();
        verify.setQuestionType("VERIFY");
        verify.setContent("배치 처리 시간은 어떻게 측정하셨나요?");
        verify.setEvidenceId(1L);
        qr.setQuestions(List.of(mine, verify));
        org.mockito.ArgumentCaptor<String> input = org.mockito.ArgumentCaptor.forClass(String.class);
        when(aiClient.call(eq(AiPurpose.QUESTION), anyLong(), anyString(), input.capture(), eq(QuestionResult.class), any()))
                .thenAnswer(inv -> {
                    java.util.function.Consumer<QuestionResult> check = inv.getArgument(5);
                    check.accept(qr);
                    return qr;
                });

        // 기본 유형 이름은 그 유형으로, 나머지는 직접 적은 유형 (쉼표가 있어도 한 유형)
        var result = extractionService.extractQuestionsByNames(id, List.of(" 거짓말 판독기 ", "협업, 갈등 경험", "", "협업, 갈등 경험"));
        assertThat(result.count()).isEqualTo(2);
        assertThat(input.getValue()).contains("<question_types>VERIFY, CUSTOM_1</question_types>", "<custom_types>",
                "\"code\" : \"CUSTOM_1\"", "\"name\" : \"협업, 갈등 경험\"").doesNotContain("CUSTOM_2");
        ResultReport pooled = reportService.report(id);
        assertThat(pooled.getExtracted()).extracting(SectionView::getSection).containsExactly("거짓말 판독기", "협업, 갈등 경험");
        assertThat(pooled.getExtracted().get(1).getQuestions().get(0).getQuestion())
                .extracting("questionType", "typeLabel", "criterionId", "evidenceId")
                .containsExactly(QuestionType.CUSTOM, "협업, 갈등 경험", null, null);

        assertThatThrownBy(() -> extractionService.extractQuestionsByNames(id, List.of(" ", "")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("유형");
    }

    @Test
    void opinionGetsAnswersButNoPersonalDataAndARecheckClearsIt() {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);
        Long id = extractionService.uploadResume(startPosition(positionService, "백엔드 개발자"), List.of(pdf()));
        extractionService.checkFit(id, null);
        extractionService.extractQuestions(id, QuestionType.SELECTABLE);
        extractionService.mergeQuestions(id);
        InterviewQuestion custom = reportService.report(id).getSections().stream().flatMap(s -> s.getQuestions().stream())
                .map(QuestionView::getQuestion).filter(q -> q.getOrigin() == QuestionOrigin.CUSTOM).findFirst().orElseThrow();
        extractionService.saveAnswer(id, custom.getId(), "nGrinder로 p95를 측정했다고 답함");

        OpinionResult.Point point = new OpinionResult.Point();
        point.setPoint("대용량 배치 개선 경험이 면접 답변으로 확인됨");
        point.setBasis("면접 답변");
        OpinionResult opinion = new OpinionResult();
        opinion.setHireReasons(List.of(point));
        opinion.setNoHireReasons(List.of());
        org.mockito.ArgumentCaptor<String> input = org.mockito.ArgumentCaptor.forClass(String.class);
        when(aiClient.call(eq(AiPurpose.OPINION), anyLong(), anyString(), input.capture(), eq(OpinionResult.class), any()))
                .thenReturn(opinion);

        // 뒤에서 받는다: 상태를 물어 끝났는지 안다
        assertThat(opinionService.status(id)).containsEntry("state", "NONE");
        opinionService.start(id);
        long until = System.currentTimeMillis() + 5000;
        while (!"DONE".equals(opinionService.status(id).get("state")) && System.currentTimeMillis() < until) {
            Thread.onSpinWait();
        }
        assertThat(opinionService.status(id)).containsEntry("state", "DONE").doesNotContainKey("error");

        assertThat(input.getValue()).contains("<conditions>", "경력 조건", "<criteria>", "Q. " + custom.getContent(),
                "A. nGrinder로 p95를 측정했다고 답함", "답변을 기록한 질문 1개")
                .doesNotContain("홍길동", "010-1111-2222", "가나커머스", "5,000만원");
        OpinionResult saved = reportService.report(id).getOpinion();
        assertThat(saved.getHireReasons()).extracting(OpinionResult.Point::getPoint).containsExactly("대용량 배치 개선 경험이 면접 답변으로 확인됨");
        assertThat(saved.getAskedAt()).isNotBlank();

        // 근거가 바뀌면 이전 의견은 지운다
        extractionService.checkFit(id, null);
        assertThat(reportService.report(id).getOpinion()).isNull();
    }

    @Test
    void transcriptBecomesOwnRecordAndReportCopiesItWithoutTouchingTheSheet() {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);
        Long id = extractionService.uploadResume(startPosition(positionService, "백엔드 개발자"), List.of(pdf()));
        extractionService.checkFit(id, null);
        extractionService.extractQuestions(id, QuestionType.SELECTABLE);
        extractionService.mergeQuestions(id);
        List<InterviewQuestion> sheet = reportService.report(id).getSections().stream().flatMap(s -> s.getQuestions().stream())
                .map(QuestionView::getQuestion).toList();
        extractionService.saveAnswer(id, sheet.get(1).getId(), "면접관이 직접 적은 답");

        String transcript = "참석자 1 00:10\n자기소개 부탁드립니다\n\n참석자 2 00:15\n저는 백엔드 개발자고요   주문 배치를 맡았습니다\n"
                + "참석자 2 03:10\n혹시 입사하면 어떤 일을 먼저 하나요\n참석자 1 03:15\n정산 유지보수부터 합니다\n";
        InterviewRecordResult ai = new InterviewRecordResult();
        ai.setItems(List.of(recordItem("INTERVIEWER", "자기소개를 부탁드립니다.", "백엔드 개발자로 주문 배치를 맡았다고 답함.",
                        "저는 백엔드 개발자고요 주문 배치를 맡았습니다"),
                recordItem("CANDIDATE", "입사 후 처음 맡는 업무는 무엇인가요?", "정산 유지보수부터 시작한다고 안내함.", "녹음본에 없는 말"),
                recordItem("INTERVIEWER", "주문 배치 규모는 어느 정도였나요?", "답변 없음", null)));
        org.mockito.ArgumentCaptor<String> input = org.mockito.ArgumentCaptor.forClass(String.class);
        when(aiClient.call(eq(AiPurpose.INTERVIEW_RECORD), anyLong(), anyString(), input.capture(), eq(InterviewRecordResult.class), any()))
                .thenReturn(ai);

        MockMultipartFile file = new MockMultipartFile("file", "녹취.txt", "text/plain",
                transcript.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        InterviewRecord record = interviewRecordService.create(id, null, file);

        assertThat(input.getValue()).contains("<transcript>", "주문 배치를 맡았습니다");
        assertThat(record.getItems()).extracting(QaItem::getKind, QaItem::getExcerpt).containsExactly(
                tuple(QaItem.INTERVIEWER, "저는 백엔드 개발자고요 주문 배치를 맡았습니다"),
                tuple(QaItem.CANDIDATE, ""), tuple(QaItem.INTERVIEWER, ""));
        Candidate saved = candidateService.get(id);
        assertThat(saved.getTranscript()).as("녹음본 원문은 저장한다").contains("정산 유지보수부터 합니다");
        assertThat(InterviewRecordService.parse(saved.getRecordJson()).getItems()).hasSize(3);
        assertThat(reportService.report(id).getSections().stream().flatMap(s -> s.getQuestions().stream())
                .map(q -> q.getQuestion().getAnswer())).as("질문지 답변은 건드리지 않는다").containsOnlyOnce("면접관이 직접 적은 답");
        assertThatThrownBy(() -> interviewRecordService.create(id, "  ", null)).isInstanceOf(BusinessException.class);

        // 보고서: 면접 기록에서 질문과 답변을 복사하고(면접관 질문 먼저), 지원자 정보 일부는 GPT가 찾는다
        ReportInfoResult info = new ReportInfoResult();
        info.setDispatch("가능");
        info.setStartDate("합격 후 2주 뒤");
        info.setDesiredSalary("4,500만원");
        org.mockito.ArgumentCaptor<String> infoInput = org.mockito.ArgumentCaptor.forClass(String.class);
        when(aiClient.call(eq(AiPurpose.REPORT_INFO), anyLong(), anyString(), infoInput.capture(), eq(ReportInfoResult.class), any()))
                .thenReturn(info);
        InterviewReport report = interviewReportService.create(id);

        assertThat(infoInput.getValue()).contains("<interviewer_notes>", "면접관이 직접 적은 답", "<transcript>", "정산 유지보수부터 합니다");
        assertThat(report.getQaSource()).isEqualTo(InterviewReport.RECORD);
        assertThat(report.getQa()).extracting(QaItem::getQuestion).containsExactly(
                "자기소개를 부탁드립니다.", "주문 배치 규모는 어느 정도였나요?", "입사 후 처음 맡는 업무는 무엇인가요?");
        assertThat(report.getInfo()).extracting(InterviewReport.InfoRow::getLabel).containsExactly(
                "성명", "나이", "경력", "학력", "정보처리기사", "SQLD", "파견 가능 여부", "입사 가능 시기", "희망연봉");
        assertThat(report.getInfo()).extracting(InterviewReport.InfoRow::getValue)
                .contains("홍길동", "총 2년 11개월 (포지션 관련 2년 6개월)", "있음", "없음", "가능", "합격 후 2주 뒤", "4,500만원");
        assertThat(report.getOpinions()).hasSize(1);

        // 보고서 버튼을 다시 누르면 지금 기록에 맞춘다: 고친 정보는 되돌리고, 면접관 의견·지원 포지션은 남기고,
        // 면접 내용이 그대로면 GPT에 다시 묻지 않는다
        report.getInfo().removeIf(r -> r.getLabel().equals("희망연봉"));
        report.getInfo().get(0).setValue("고친 이름");
        report.getOpinions().get(0).setContent("기술 깊이가 좋음");
        report.setPosition("백엔드 (보고서에서 고침)");
        interviewReportService.saveEdited(id, report);
        report = interviewReportService.create(id);
        verify(aiClient, org.mockito.Mockito.times(1)).call(eq(AiPurpose.REPORT_INFO), anyLong(), anyString(), anyString(), any(), any());
        assertThat(report.getInfo()).extracting(InterviewReport.InfoRow::getLabel, InterviewReport.InfoRow::getValue)
                .contains(tuple("성명", "홍길동"), tuple("희망연봉", "4,500만원"));
        assertThat(report.getOpinions()).extracting(InterviewReport.Opinion::getContent).containsExactly("기술 깊이가 좋음");
        assertThat(report.getPosition()).isEqualTo("백엔드 (보고서에서 고침)");
        assertThat(report.getQa()).hasSize(3);

        // 질문지 답변이 바뀌면 다시 묻는다
        ReportInfoResult again = new ReportInfoResult();
        again.setDispatch("가능 (수도권)");
        again.setStartDate("");
        again.setDesiredSalary("5,800만원");
        when(aiClient.call(eq(AiPurpose.REPORT_INFO), anyLong(), anyString(), anyString(), eq(ReportInfoResult.class), any()))
                .thenReturn(again);
        extractionService.saveAnswer(id, sheet.get(1).getId(), "희망연봉은 5,800만원이라고 함");
        report = interviewReportService.create(id);
        verify(aiClient, org.mockito.Mockito.times(2)).call(eq(AiPurpose.REPORT_INFO), anyLong(), anyString(), anyString(), any(), any());
        assertThat(report.getInfo()).extracting(InterviewReport.InfoRow::getLabel, InterviewReport.InfoRow::getValue)
                .contains(tuple("파견 가능 여부", "가능 (수도권)"), tuple("입사 가능 시기", ""), tuple("희망연봉", "5,800만원"));

        // 보고서를 고쳐도 면접 기록은 그대로
        report.getQa().get(0).setAnswer("보고서에서 고친 답");
        report.getQa().remove(2);
        interviewReportService.save(id, report);
        assertThat(interviewReportService.get(id).getQa()).hasSize(2);
        assertThat(InterviewRecordService.parse(candidateService.get(id).getRecordJson()).getItems().get(0).getAnswer())
                .isEqualTo("백엔드 개발자로 주문 배치를 맡았다고 답함.");

        // 질문지로 바꾸면 면접관이 적은 답변만 가져온다
        interviewReportService.useSource(id, InterviewReport.SHEET);
        assertThat(interviewReportService.get(id).getQa()).extracting(QaItem::getAnswer).containsExactly("희망연봉은 5,800만원이라고 함");
        // 고른 출처는 다시 맞춰도 그대로
        assertThat(interviewReportService.create(id).getQaSource()).isEqualTo(InterviewReport.SHEET);
    }

    private static InterviewRecordResult.Item recordItem(String kind, String question, String answer, String excerpt) {
        InterviewRecordResult.Item i = new InterviewRecordResult.Item();
        i.setKind(kind);
        i.setQuestion(question);
        i.setAnswer(answer);
        i.setExcerpt(excerpt);
        return i;
    }
    @Test
    void rejectsBadInputBeforeSavingAnything() {
        stubAi(aiClient, templateMapper, sectionMapper);
        Long positionId = startPosition(positionService, "포지션");
        MockMultipartFile notPdf = new MockMultipartFile("resume", "resume.docx", "application/octet-stream", new byte[] { 1 });
        assertThatThrownBy(() -> extractionService.uploadResume(positionId, List.of(notPdf))).isInstanceOf(ResumeProcessingException.class);
        assertThatThrownBy(() -> extractionService.uploadResume(positionId, null)).isInstanceOf(ResumeProcessingException.class);
        assertThat(extractionService.candidates(positionId)).isEmpty();

        Long empty = positionService.create("기준 없는 포지션");
        assertThatThrownBy(() -> extractionService.uploadResume(empty, List.of(pdf()))).isInstanceOf(BusinessException.class);
    }

    @Test
    void combinesSeveralFilesWithoutFileNames() throws Exception {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);
        byte[] docx;
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(); var out = new java.io.ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("포트폴리오: 홍길동의 정산 배치 개선 프로젝트");
            doc.write(out);
            docx = out.toByteArray();
        }
        MockMultipartFile portfolio = new MockMultipartFile("resume", "홍길동_포트폴리오.docx",
                "application/octet-stream", docx);

        Long id = extractionService.uploadResume(startPosition(positionService, "백엔드 개발자"), List.of(pdf(), portfolio));

        assertThat(candidateService.get(id).getMaskedText())
                .containsSubsequence("[첨부 1 (PDF)]", "[첨부 2 (DOCX)]", "포트폴리오: [이름]의 정산 배치 개선 프로젝트")
                .doesNotContain("홍길동", "포트폴리오.docx");
    }

    @Test
    void reviewedTextWithPhoneNumberIsRejectedBeforeAi() {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);
        Long id = extractionService.uploadResume(startPosition(positionService, "백엔드 개발자"), List.of(pdf()));
        String saved = candidateService.get(id).getMaskedText();
        reset(aiClient);

        assertThatThrownBy(() -> extractionService.checkFit(id, saved + "\n연락처 010-9999-8888"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("휴대전화");
        assertThatThrownBy(() -> extractionService.checkFit(id, "  "))
                .isInstanceOf(BusinessException.class);

        assertThat(candidateService.get(id).getMaskedText()).isEqualTo(saved);
        assertThat(candidateService.get(id).getStatus()).isEqualTo(CandidateStatus.UPLOADED);
        org.mockito.Mockito.verifyNoInteractions(aiClient);
    }

    @Test
    void editingTheMaskedTextAfterACheckRechecksAndDropsTheSheet() {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);
        Long id = extractionService.uploadResume(startPosition(positionService, "백엔드 개발자"), List.of(pdf()));
        extractionService.checkFit(id, null);
        extractionService.extractQuestions(id, QuestionType.SELECTABLE);
        extractionService.mergeQuestions(id);
        assertThat(reportService.report(id).getSections()).isNotEmpty();

        String edited = candidateService.get(id).getMaskedText().replace("Redis", "[개인정보]");
        extractionService.checkFit(id, edited);

        ResultReport r = reportService.report(id);
        assertThat(r.isFitChecked()).isTrue();
        assertThat(r.getSections()).as("본문이 바뀌면 질문지는 다시 만든다").isEmpty();
        assertThat(r.getCandidate().getMaskedText()).contains("[개인정보] 캐시 도입");
    }

    @Test
    void batchMasksAndChecksEachApplicantInTurnWithoutReview() throws Exception {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);
        Long positionId = startPosition(positionService, "백엔드 개발자 채용");

        // 빈 칸은 건너뛰고, 한 칸에 여러 파일이면 한 사람
        List<Long> started = batchCheckService.start(positionId, List.of(List.of(pdf()), List.of(), List.of(pdf(), pdf())));
        assertThat(started).hasSize(2);
        // 원본 파일은 올린 그대로 다시 내려받을 수 있다
        assertThat(candidateService.files(started.get(1))).extracting("fileName", "contentType")
                .containsExactly(tuple("resume.pdf", "application/pdf"), tuple("resume.pdf", "application/pdf"));
        Long fileId = candidateService.files(started.get(0)).get(0).getId();
        assertThat(candidateService.file(started.get(0), fileId).getFileData()).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> candidateService.file(started.get(1), fileId)).isInstanceOf(NotFoundException.class);
        batchCheckService.awaitIdle();

        List<com.example.interview.web.dto.RankingRow> rows = rankingService.rank(positionId);
        assertThat(rows).extracting("displayName", "state", "rank")
                .containsExactlyInAnyOrder(tuple("홍길동", "RANKED", 1), tuple("홍길동", "RANKED", 2));
        for (var row : rows) {
            Candidate c = candidateService.get(row.getCandidateId());
            assertThat(c.getStatus()).isEqualTo(CandidateStatus.ANALYZED);
            assertThat(c.getMaskedText()).contains("[전화번호]").doesNotContain("010-1111-2222", "홍길동");
            assertThat(reportService.report(c.getId()).getSections()).as("질문은 면접관이 직접 뽑는다").isEmpty();
        }
        verify(aiClient, never()).call(eq(AiPurpose.QUESTION), anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void strictPrivacyModeStopsAfterMaskingUntilReviewed() throws Exception {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);
        Long positionId = startPosition(positionService, "포지션");
        privacyModeService.setStrict(true);
        try {
            assertThatThrownBy(() -> batchCheckService.start(positionId, List.of(List.of(pdf()), List.of(pdf()))))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("한 명씩");

            Long id = batchCheckService.start(positionId, List.of(List.of(pdf()))).get(0);
            batchCheckService.awaitIdle();
            Candidate c = candidateService.get(id);
            assertThat(c.getStatus()).as("가린 뒤 사람이 확인할 때까지 멈춘다").isEqualTo(CandidateStatus.UPLOADED);
            assertThat(c.getMaskedText()).contains("[전화번호]");
            verify(aiClient, never()).call(eq(AiPurpose.EVIDENCE), anyLong(), anyString(), anyString(), any(), any());

            // 확인·수정한 글로 검사한다
            extractionService.checkFit(id, c.getMaskedText().replace("Redis", "[개인정보]"));
            Candidate checked = candidateService.get(id);
            assertThat(checked.getStatus()).isEqualTo(CandidateStatus.ANALYZED);
            assertThat(checked.getMaskedText()).contains("[개인정보] 캐시 도입");
            verify(aiClient).call(eq(AiPurpose.EVIDENCE), eq(id), anyString(), anyString(), any(), any());
        } finally {
            privacyModeService.setStrict(false);
        }
    }

    @Test
    void batchApplicantWhoseMaskingFailsStopsWithoutReachingGpt() throws Exception {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(aiClient.callLocal(eq(AiPurpose.PII_DETECT), anyLong(), anyString(), anyString(), any(),
                eq(PiiExtract.class), any())).thenThrow(new AiCallException("Connection refused"));
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);
        Long positionId = startPosition(positionService, "포지션");

        batchCheckService.start(positionId, List.of(List.of(pdf())));
        batchCheckService.awaitIdle();

        Candidate c = candidateService.get(rankingService.rank(positionId).get(0).getCandidateId());
        assertThat(c.getStatus()).isEqualTo(CandidateStatus.FAILED);
        assertThat(c.getMaskedText()).isNull();
        assertThat(c.getErrorMessage()).contains("개인정보를 가리지 못했습니다");
        verify(aiClient, never()).call(eq(AiPurpose.EVIDENCE), anyLong(), anyString(), anyString(), any(), any());

        // 저장해 둔 원본 파일로 가리기부터 다시 검사한다
        stubPii(aiClient, pii(PiiResult.Type.APPLICANT_NAME, "홍길동"), pii(PiiResult.Type.PHONE, "010-1111-2222"));
        batchCheckService.restart(c.getId());
        batchCheckService.awaitIdle();
        Candidate again = candidateService.get(c.getId());
        assertThat(again.getMaskedText()).contains("[전화번호]").doesNotContain("010-1111-2222");
        verify(aiClient).call(eq(AiPurpose.EVIDENCE), eq(c.getId()), anyString(), anyString(), any(), any());

        Long other = candidateService.createQueued(positionId, "파일 없음");
        assertThatThrownBy(() -> batchCheckService.restart(other)).isInstanceOf(BusinessException.class);
    }

    @Test
    void unfinishedBatchWorkIsMarkedFailedOnRestart() {
        stubAi(aiClient, templateMapper, sectionMapper);
        Long positionId = startPosition(positionService, "포지션");
        Long id = candidateService.createQueued(positionId, "지원자");
        // 진행 중에는 이름을 바꿀 수 없다 (가리기가 끝나면 이름을 덮어쓴다)
        assertThatThrownBy(() -> candidateService.rename(id, "새 이름")).isInstanceOf(BusinessException.class);
        batchCheckService.failUnfinished();
        assertThat(candidateService.get(id).getStatus()).isEqualTo(CandidateStatus.FAILED);
        assertThat(candidateService.get(id).getErrorMessage()).isEqualTo(BatchCheckService.INTERRUPTED);
        assertThat(candidateService.rename(id, "새 이름")).isEqualTo("새 이름");
    }

    @Test
    void localAiFailureSavesNothing() {
        stubAi(aiClient, templateMapper, sectionMapper);
        when(aiClient.callLocal(eq(AiPurpose.PII_DETECT), anyLong(), anyString(), anyString(), any(),
                eq(PiiExtract.class), any())).thenThrow(new AiCallException("Connection refused"));
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);
        Long positionId = startPosition(positionService, "포지션");

        assertThatThrownBy(() -> extractionService.uploadResume(positionId, List.of(pdf())))
                .isInstanceOf(ResumeProcessingException.class).hasMessageContaining("Ollama");
        assertThat(extractionService.candidates(positionId)).isEmpty();
    }

    @Test
    void phoneNumberTheLocalAiMissedIsStillMasked() {
        stubAi(aiClient, templateMapper, sectionMapper);
        stubPii(aiClient, pii(PiiResult.Type.APPLICANT_NAME, "홍길동"));
        when(pdfTextExtractor.extract(any())).thenReturn(RESUME);
        Long positionId = startPosition(positionService, "포지션");

        Long id = extractionService.uploadResume(positionId, List.of(pdf()));

        assertThat(candidateService.get(id).getMaskedText()).contains("[전화번호]").doesNotContain("010-1111-2222");
    }

    public static Long startPosition(PositionService positionService, String jdText) {
        Long id = positionService.create(jdText);
        positionService.extractCriteria(id);
        return id;
    }

    static void stubPii(AiClient aiClient, PiiResult.Item... items) {
        stubPii(aiClient, null, items);
    }

    static void stubPii(AiClient aiClient, CandidateProfile profile, PiiResult.Item... items) {
        when(aiClient.callLocal(eq(AiPurpose.PII_DETECT), anyLong(), anyString(), anyString(), any(),
                eq(PiiExtract.class), any())).thenAnswer(inv -> PiiExtracts.of(profile, items));
    }

    static PiiResult.Item pii(PiiResult.Type type, String text) {
        PiiResult.Item item = new PiiResult.Item();
        item.setType(type);
        item.setText(text);
        return item;
    }

    public static MockMultipartFile pdf() {
        return new MockMultipartFile("resume", "resume.pdf", "application/pdf", new byte[] { 1, 2, 3 });
    }

    /** 평가항목 3개, 근거(직접 1·지어낸 간접 1·없음 2), 맞춤 질문 1개 */
    public static void stubAi(AiClient aiClient, QuestionTemplateMapper templateMapper,
            QuestionSectionMapper sectionMapper) {
        CandidateProfile profile = new CandidateProfile();
        CandidateProfile.Career career = new CandidateProfile.Career();
        career.setCompany("가나커머스");
        career.setDepartment("백엔드팀");
        career.setPeriod("2021.03 ~ 2024.02");
        career.setDuration("2년 11개월");
        CandidateProfile.Education invented = new CandidateProfile.Education();
        invented.setSchool("지어낸대학교");
        profile.setCareers(List.of(career));
        profile.setEducation(List.of(invented));
        profile.setDesiredSalary("5,000만원");
        CandidateProfile.Training training = new CandidateProfile.Training();
        training.setInstitution("가나정보교육원");
        training.setName("국비 과정 수료");
        profile.setTrainings(List.of(training));
        stubPii(aiClient, profile, pii(PiiResult.Type.APPLICANT_NAME, "홍길동"), pii(PiiResult.Type.PHONE, "010-1111-2222"));

        CriteriaResult criteria = new CriteriaResult();
        criteria.setPositionTitle("백엔드 개발자 (Java)");
        criteria.setRequiredExperience("경력 3년 이상");
        criteria.setCriteria(List.of(
                criterion("대용량 트래픽 처리 경험", "TECH", true),
                criterion("Kafka 사용 경험", "TECH", true),
                criterion("책임감", "ETC", false)));
        when(aiClient.call(eq(AiPurpose.CRITERIA_EXTRACT), anyLong(), anyString(), anyString(), anyList(),
                eq(CriteriaResult.class), any())).thenReturn(criteria);

        when(aiClient.call(eq(AiPurpose.TIMELINE), anyLong(), anyString(), anyString(),
                eq(TimelineResult.class), any())).thenAnswer(inv -> {
                    String content = inv.getArgument(3);
                    if (content.contains("가나커머스") || content.contains("<resume>") || !content.contains("A사 | 2021.03 ~ 2024.02")) {
                        throw new IllegalStateException("타임라인 호출에는 이름표와 기간만 보낸다: " + content);
                    }
                    TimelineResult tr = new TimelineResult();
                    tr.setTimeline(List.of(period("A사", "2021-03", "2024-02"), period("교육1", "2020-01", "2020-06"),
                            period("없는항목", "2019-01", "2019-02")));
                    return tr;
                });

        when(aiClient.call(eq(AiPurpose.CAREER_MEETS), anyLong(), anyString(), anyString(),
                eq(CareerMeetsResult.class), any())).thenAnswer(inv -> {
                    String content = inv.getArgument(3);
                    if (content.contains("가나커머스") || content.contains("<resume>")
                            || !content.contains("<experience_requirement>경력 3년 이상</experience_requirement>")
                            || !content.contains("relevant_months: 30")) {
                        throw new IllegalStateException("경력 판단 호출에는 공고 경력 조건과 경력 개월만 보낸다: " + content);
                    }
                    CareerMeetsResult m = new CareerMeetsResult();
                    m.setCareerMeets("NOT_MET");
                    m.setCareerReason("3년 이상 조건에 관련 경력 30개월로 6개월 모자람");
                    return m;
                });

        when(aiClient.call(eq(AiPurpose.EVIDENCE), anyLong(), anyString(), anyString(),
                eq(EvidenceResult.class), any())).thenAnswer(inv -> {
                    String content = inv.getArgument(3);
                    // 회사 이름은 가리고 재직 기간은 이력서에 그대로 둔다. 회사 목록은 따로 보내지 않는다
                    if (content.contains("가나커머스") || content.contains("<careers>")
                            || !content.contains("[A사] 백엔드팀 2021.03 ~ 2024.02")) {
                        throw new IllegalStateException("근거 분석 입력이 다름: " + content);
                    }
                    // 실제 평가항목 id는 저장 후에 정해지므로 요청 본문에서 읽는다
                    List<Long> ids = java.util.regex.Pattern.compile("\"criterion_id\"\\s*:\\s*(\\d+)").matcher(content)
                            .results().map(m -> Long.valueOf(m.group(1))).toList();
                    EvidenceResult er = new EvidenceResult();
                    er.setResults(List.of(
                            result(ids.get(0), item("DIRECT", "일 100만 건 주문 데이터 배치 처리 개선"), item("INDIRECT", "지어낸 문장")),
                            result(ids.get(1), item("NONE", null)),
                            result(ids.get(2), item("NONE", null))));
                    EvidenceResult.Career calc = new EvidenceResult.Career();
                    CareerEvalResult.Company ganara = new CareerEvalResult.Company();
                    ganara.setCompany("A사");
                    ganara.setMonths(35);
                    ganara.setRelevantMonths(30);
                    calc.setPerCompany(List.of(ganara));
                    calc.setTotalMonths(35);
                    calc.setTotalReason("A사 35개월");
                    calc.setRelevantMonths(30);
                    calc.setRelevantReason("백엔드 업무 기간");
                    er.setCareer(calc);
                    CareerEvalResult.Certificates certs = new CareerEvalResult.Certificates();
                    certs.setItems(List.of());
                    certs.setInfoEngineer(true);
                    certs.setSqld(false);
                    er.setCertificates(certs);
                    er.validate(Set.copyOf(ids));
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

    private static CriteriaResult.Item criterion(String name, String cat, boolean assessable) {
        CriteriaResult.Item i = new CriteriaResult.Item();
        i.setName(name);
        i.setDescription(name + " 설명");
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

    static CareerEvalResult.Period period(String ref, String start, String end) {
        CareerEvalResult.Period p = new CareerEvalResult.Period();
        p.setRef(ref);
        p.setStart(start);
        p.setEnd(end);
        return p;
    }

    private static EvidenceResult.Item item(String type, String quote) {
        EvidenceResult.Item i = new EvidenceResult.Item();
        i.setType(type);
        i.setQuote(quote);
        i.setInterpretation("해석");
        return i;
    }
}
