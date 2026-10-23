package com.example.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.AiResponseInvalidException;
import com.example.interview.ai.dto.ArrangeResult;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.QuestionOrigin;
import com.example.interview.domain.QuestionSection;
import com.example.interview.domain.QuestionTemplate;
import com.example.interview.domain.QuestionType;

class QuestionSheetAssemblerTest {

    private final QuestionSheetAssembler assembler = new QuestionSheetAssembler();

    private final List<QuestionSection> sections = List.of(
            sec(30L, "오프닝"), sec(10L, "경험"), sec(40L, "마무리"));

    private final List<QuestionTemplate> templates = List.of(
            template(1L, 30L, "자기소개"),
            template(2L, 10L, "PM 경험?"),
            template(3L, 10L, "인프라 경험?"),
            template(4L, 40L, "궁금한 점?"));

    private final List<InterviewQuestion> questions = List.of(
            question(QuestionType.VERIFY, "40% 개선은 어떻게 측정했나요?", 10L, 99L),
            question(QuestionType.CHOICE, "Redis를 고른 이유는?", 10L, 1L));

    @Test
    void followsArrangementOrderAndSectionNames() {
        ArrangeResult arrangement = arrangement(
                section("시작", common(1L)),
                section("캐시 경험 검증", common(2L),
                        extracted(2L), extracted(1L), common(3L)),
                section("마무리", common(4L)));

        List<InterviewQuestion> sheet = assembler.assemble(7L, sections, templates, questions, arrangement);

        assertThat(sheet).extracting(InterviewQuestion::getContent).containsExactly(
                "자기소개", "PM 경험?", "Redis를 고른 이유는?",
                "40% 개선은 어떻게 측정했나요?", "인프라 경험?", "궁금한 점?");
        assertThat(sheet).extracting(InterviewQuestion::getSection)
                .containsExactly("시작", "캐시 경험 검증", "캐시 경험 검증", "캐시 경험 검증", "캐시 경험 검증", "마무리");
        assertThat(sheet).extracting(InterviewQuestion::getSortOrder).containsExactly(1, 2, 3, 4, 5, 6);

        assertThat(sheet.get(1).getOrigin()).as("공통 질문은 원문 그대로").isEqualTo(QuestionOrigin.COMMON_ORIGINAL);
        assertThat(sheet.get(1).getOriginalContent()).isNull();
        assertThat(sheet.get(4).getOrigin()).isEqualTo(QuestionOrigin.COMMON_ORIGINAL);

        InterviewQuestion verify = sheet.get(3);
        assertThat(verify.getOrigin()).isEqualTo(QuestionOrigin.CUSTOM);
        assertThat(verify.getQuestionType()).isEqualTo(QuestionType.VERIFY);
        assertThat(verify).as("추출해 둔 행을 그대로 넣는다").isSameAs(questions.get(0));
        assertThat(verify.isMerged()).isTrue();
        assertThat(verify.getCriterionId()).isEqualTo(10L);
        assertThat(verify.getFollowUps()).isEqualTo("[\"꼬리질문1\"]");
    }

    @Test
    void missingSectionAndQuestionGoNextToTheirNeighbors() {
        ArrangeResult arrangement = arrangement(
                section("시작", common(1L), extracted(1L)),
                section("마무리", common(4L)));

        List<InterviewQuestion> sheet = assembler.assemble(1L, sections, templates, questions, arrangement);

        assertThat(sheet).extracting(InterviewQuestion::getContent).containsExactly(
                "자기소개", "40% 개선은 어떻게 측정했나요?", "Redis를 고른 이유는?", "PM 경험?", "인프라 경험?", "궁금한 점?");
        assertThat(sheet).extracting(InterviewQuestion::getSection)
                .containsExactly("시작", "시작", "시작", "경험", "경험", "마무리");
        assertThat(sheet).extracting(InterviewQuestion::getSortOrder).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void missingCommonQuestionJoinsItsSectionNeighbor() {
        ArrangeResult arrangement = arrangement(
                section("시작", common(1L)),
                section("캐시 경험", extracted(1L), common(3L), extracted(2L)),
                section("마무리", common(4L)));

        List<InterviewQuestion> sheet = assembler.assemble(1L, sections, templates, questions, arrangement);

        assertThat(sheet).extracting(InterviewQuestion::getContent).containsExactly(
                "자기소개", "40% 개선은 어떻게 측정했나요?", "PM 경험?", "인프라 경험?", "Redis를 고른 이유는?", "궁금한 점?");
        assertThat(sheet.get(2).getSection()).isEqualTo("캐시 경험");
    }

    @Test
    void laterExtractionGoesNextToTheSameCriterionWithoutMovingTheSheet() {
        InterviewQuestion intro = sheetRow("오프닝", QuestionOrigin.COMMON_ORIGINAL, null, 1);
        InterviewQuestion cache = sheetRow("캐시", QuestionOrigin.CUSTOM, 10L, 2);
        InterviewQuestion close = sheetRow("마무리", QuestionOrigin.COMMON_ORIGINAL, null, 3);
        InterviewQuestion sameCriterion = question(QuestionType.DEPTH, "캐시 무효화는?", 10L, 1L);
        InterviewQuestion other = question(QuestionType.EXPERIENCE, "Kafka 해보셨나요?", 20L, null);

        List<InterviewQuestion> sheet = assembler.insert(List.of(intro, cache, close), List.of(sameCriterion, other));

        assertThat(sheet).containsExactly(intro, cache, sameCriterion, other, close);
        assertThat(sheet).extracting(InterviewQuestion::getSection).containsExactly("오프닝", "캐시", "캐시", "캐시", "마무리");
        assertThat(sheet).extracting(InterviewQuestion::getSortOrder).containsExactly(1, 2, 3, 4, 5);

        List<InterviewQuestion> noAnchor = assembler.insert(List.of(intro), List.of(question(QuestionType.EXPERIENCE, "x", 20L, null)));
        assertThat(noAnchor).extracting(InterviewQuestion::getSection).containsExactly("오프닝", "추가 질문");
    }

    @Test
    void placesNewQuestionsWhereTheAiSaidAndFallsBackForTheRest() {
        InterviewQuestion intro = sheetRow("오프닝", QuestionOrigin.COMMON_ORIGINAL, null, 1);
        InterviewQuestion cache = sheetRow("캐시", QuestionOrigin.CUSTOM, 10L, 2);
        InterviewQuestion close = sheetRow("마무리", QuestionOrigin.COMMON_ORIGINAL, null, 3);
        InterviewQuestion a = question(QuestionType.EXPERIENCE, "Kafka 해보셨나요?", 20L, null);
        InterviewQuestion b = question(QuestionType.EXPERIENCE, "Kafka 장애는?", 20L, null);
        InterviewQuestion c = question(QuestionType.DEPTH, "캐시 무효화는?", 10L, 1L);

        // a, b는 1번(오프닝) 뒤에 순서대로, c는 자리가 없어 같은 평가 기준(캐시) 뒤에
        List<InterviewQuestion> sheet = assembler.insert(List.of(intro, cache, close), List.of(a, b, c),
                java.util.Map.of(0, 1, 1, 1));

        assertThat(sheet).containsExactly(intro, a, b, cache, c, close);
        assertThat(sheet).extracting(InterviewQuestion::getSection).containsExactly("오프닝", "오프닝", "오프닝", "캐시", "캐시", "마무리");
    }

    @Test
    void placeValidationDropsBadNumbers() {
        com.example.interview.ai.dto.PlaceResult r = new com.example.interview.ai.dto.PlaceResult();
        r.setPlacements(new java.util.ArrayList<>(List.of(placement(1L, 2), placement(1L, 3), placement(2L, 9), placement(5L, 1))));
        r.validate(3, 2);
        assertThat(r.getPlacements()).extracting(com.example.interview.ai.dto.PlaceResult.Placement::getQuestionId).containsExactly(1L);
    }

    private static com.example.interview.ai.dto.PlaceResult.Placement placement(Long id, int after) {
        com.example.interview.ai.dto.PlaceResult.Placement p = new com.example.interview.ai.dto.PlaceResult.Placement();
        p.setQuestionId(id);
        p.setAfter(after);
        return p;
    }

    private static InterviewQuestion sheetRow(String section, QuestionOrigin origin, Long criterionId, int order) {
        InterviewQuestion q = new InterviewQuestion();
        q.setSection(section);
        q.setOrigin(origin);
        q.setCriterionId(criterionId);
        q.setSortOrder(order);
        return q;
    }

    @Test
    void validationDropsUnknownAndDuplicateIds() {
        ArrangeResult r = arrangement(section("A", common(9L), extracted(1L), extracted(1L), extracted(10L)),
                section("B", extracted(3L)));
        r.validate(List.of(1L, 2L), 2);
        assertThat(r.getSections()).hasSize(1);
        assertThat(r.getSections().get(0).getItems()).extracting(ArrangeResult.Item::getQuestionId).containsExactly(1L);

        assertThatThrownBy(() -> arrangement(section("A", extracted(10L))).validate(List.of(), 2))
                .isInstanceOf(AiResponseInvalidException.class);
    }

    private static ArrangeResult arrangement(ArrangeResult.Section... sections) {
        ArrangeResult r = new ArrangeResult();
        r.setSections(List.of(sections));
        return r;
    }

    private static ArrangeResult.Section section(String name, ArrangeResult.Item... items) {
        ArrangeResult.Section s = new ArrangeResult.Section();
        s.setName(name);
        s.setItems(List.of(items));
        return s;
    }

    private static ArrangeResult.Item common(Long templateId) {
        ArrangeResult.Item i = new ArrangeResult.Item();
        i.setTemplateId(templateId);
        return i;
    }

    private static ArrangeResult.Item extracted(Long questionId) {
        ArrangeResult.Item i = new ArrangeResult.Item();
        i.setQuestionId(questionId);
        return i;
    }

    private static QuestionSection sec(Long id, String name) {
        QuestionSection s = new QuestionSection();
        s.setId(id);
        s.setName(name);
        return s;
    }

    private static QuestionTemplate template(Long id, Long sectionId, String content) {
        QuestionTemplate t = new QuestionTemplate();
        t.setId(id);
        t.setSectionId(sectionId);
        t.setContent(content);
        return t;
    }

    private static InterviewQuestion question(QuestionType type, String content, Long criterionId, Long evidenceId) {
        InterviewQuestion q = new InterviewQuestion();
        q.setOrigin(QuestionOrigin.CUSTOM);
        q.setQuestionType(type);
        q.setContent(content);
        q.setIntent("의도");
        q.setFollowUps("[\"꼬리질문1\"]");
        q.setSection("");
        q.setMerged(false);
        q.setPriority(1);
        q.setCriterionId(criterionId);
        q.setEvidenceId(evidenceId);
        return q;
    }
}
