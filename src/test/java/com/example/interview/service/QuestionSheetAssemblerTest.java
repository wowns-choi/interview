package com.example.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.AiResponseInvalidException;
import com.example.interview.ai.dto.ArrangeResult;
import com.example.interview.ai.dto.QuestionResult;
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

    private final List<QuestionResult.Item> questions = List.of(
            question("VERIFY", "40% 개선은 어떻게 측정했나요?", 10L, 99L),
            question("DEPTH", "Redis를 고른 이유는?", 10L, 1L));

    @Test
    void followsArrangementOrderAndSectionNames() {
        ArrangeResult arrangement = arrangement(
                section("시작", common(1L, false, null, null)),
                section("캐시 경험 검증", common(2L, true, "A 프로젝트 일정 관리는 어떻게 하셨나요?", 1L),
                        extracted(2L), extracted(1L), common(3L, false, "무시되는 문구", null)),
                section("마무리", common(4L, false, null, null)));

        List<InterviewQuestion> sheet = assembler.assemble(7L, sections, templates, questions, arrangement,
                Set.of(10L), Set.of(1L));

        assertThat(sheet).extracting(InterviewQuestion::getContent).containsExactly(
                "자기소개", "A 프로젝트 일정 관리는 어떻게 하셨나요?", "Redis를 고른 이유는?",
                "40% 개선은 어떻게 측정했나요?", "인프라 경험?", "궁금한 점?");
        assertThat(sheet).extracting(InterviewQuestion::getSection)
                .containsExactly("시작", "캐시 경험 검증", "캐시 경험 검증", "캐시 경험 검증", "캐시 경험 검증", "마무리");
        assertThat(sheet).extracting(InterviewQuestion::getSortOrder).containsExactly(1, 2, 3, 4, 5, 6);

        InterviewQuestion personalized = sheet.get(1);
        assertThat(personalized.getOrigin()).isEqualTo(QuestionOrigin.COMMON_PERSONALIZED);
        assertThat(personalized.getEvidenceId()).isEqualTo(1L);
        assertThat(sheet.get(4).getOrigin()).isEqualTo(QuestionOrigin.COMMON_ORIGINAL);

        InterviewQuestion verify = sheet.get(3);
        assertThat(verify.getOrigin()).isEqualTo(QuestionOrigin.CUSTOM);
        assertThat(verify.getQuestionType()).isEqualTo(QuestionType.VERIFY);
        assertThat(verify.getEvidenceId()).as("존재하지 않는 근거는 연결하지 않음").isNull();
        assertThat(verify.getCriterionId()).isEqualTo(10L);
        assertThat(verify.getFollowUps()).isEqualTo("[\"꼬리질문1\"]");
    }

    @Test
    void missingSectionAndQuestionGoNextToTheirNeighbors() {
        ArrangeResult arrangement = arrangement(
                section("시작", common(1L, false, null, null), extracted(1L)),
                section("마무리", common(4L, false, null, null)));

        List<InterviewQuestion> sheet = assembler.assemble(1L, sections, templates, questions, arrangement,
                Set.of(10L), Set.of());

        assertThat(sheet).extracting(InterviewQuestion::getContent).containsExactly(
                "자기소개", "40% 개선은 어떻게 측정했나요?", "Redis를 고른 이유는?", "PM 경험?", "인프라 경험?", "궁금한 점?");
        assertThat(sheet).extracting(InterviewQuestion::getSection)
                .containsExactly("시작", "시작", "시작", "경험", "경험", "마무리");
        assertThat(sheet).extracting(InterviewQuestion::getSortOrder).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void missingCommonQuestionJoinsItsSectionNeighbor() {
        ArrangeResult arrangement = arrangement(
                section("시작", common(1L, false, null, null)),
                section("캐시 경험", extracted(1L), common(3L, false, null, null), extracted(2L)),
                section("마무리", common(4L, false, null, null)));

        List<InterviewQuestion> sheet = assembler.assemble(1L, sections, templates, questions, arrangement,
                Set.of(), Set.of());

        assertThat(sheet).extracting(InterviewQuestion::getContent).containsExactly(
                "자기소개", "40% 개선은 어떻게 측정했나요?", "PM 경험?", "인프라 경험?", "Redis를 고른 이유는?", "궁금한 점?");
        assertThat(sheet.get(2).getSection()).isEqualTo("캐시 경험");
    }

    @Test
    void validationRejectsUnknownAndDuplicateIdsButAllowsMissing() {
        arrangement(section("A", common(1L, false, null, null), extracted(1L))).validate(List.of(1L, 2L), 2);

        ArrangeResult bad = arrangement(section("A", common(9L, false, null, null), extracted(1L), extracted(1L)));
        assertThatThrownBy(() -> bad.validate(List.of(1L, 2L), 2))
                .isInstanceOf(AiResponseInvalidException.class)
                .hasMessageContaining("template_id 9").hasMessageContaining("question_id 1");
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

    private static ArrangeResult.Item common(Long templateId, boolean personalized, String content, Long evidenceId) {
        ArrangeResult.Item i = new ArrangeResult.Item();
        i.setTemplateId(templateId);
        i.setPersonalized(personalized);
        i.setContent(content);
        i.setEvidenceId(evidenceId);
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

    private static QuestionResult.Item question(String type, String content, Long criterionId, Long evidenceId) {
        QuestionResult.Item q = new QuestionResult.Item();
        q.setQuestionType(type);
        q.setContent(content);
        q.setIntent("의도");
        q.setFollowUps(List.of("꼬리질문1"));
        q.setPriority(1);
        q.setCriterionId(criterionId);
        q.setEvidenceId(evidenceId);
        return q;
    }
}
