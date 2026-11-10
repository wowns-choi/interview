package com.example.interview.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.AiResponseInvalidException;

class QuestionResultTest {

    private static final Set<String> ALL = Set.of("VERIFY", "DEPTH", "CHOICE", "EXPERIENCE");
    private static final Set<String> CLAIM = Set.of("VERIFY", "DEPTH", "CHOICE");

    @Test
    void keepsQuestionsByTheVerdictOfTheirCriterionNotTheEvidenceNumber() {
        // 평가항목 10: 직접 근거(근거 1), 11: 간접 근거만(근거 2), 12: 미확인
        QuestionResult r = result(
                item("VERIFY", "근거 1 질문", 99L, 1L),
                item("DEPTH", "근거 번호 없음, 충족 평가항목", 10L, null),
                item("CHOICE", "근거 번호 없음, 부분 충족 평가항목", 11L, null),
                item("DEPTH", "클린 아키텍처를 적용하셨다고 하셨는데", 12L, null),
                item("DEPTH", "없는 근거, 연결 없음", null, 77L),
                item("EXPERIENCE", "Kafka 해보셨나요?", 12L, 3L),
                item("EXPERIENCE", "부분 충족 평가항목도 물을 수 있다", 11L, null),
                item("EXPERIENCE", "충족 평가항목은 이미 해 봤다", 10L, null),
                item("VERIFY", " ", 10L, 1L));

        r.validate(Set.of("VERIFY", "DEPTH", "CHOICE", "EXPERIENCE"), Map.of(1L, 10L, 2L, 11L), Set.of(11L, 12L), CLAIM);

        // 근거 번호가 없어도 평가항목 판정이 맞으면 남긴다 (사용자 2026-11-09), 미확인 평가항목의 근거 질문은 버린다 (사용자 2026-11-10)
        assertThat(r.getQuestions()).extracting(QuestionResult.Item::getContent).containsExactly(
                "근거 1 질문", "근거 번호 없음, 충족 평가항목", "근거 번호 없음, 부분 충족 평가항목",
                "Kafka 해보셨나요?", "부분 충족 평가항목도 물을 수 있다");
        assertThat(r.getQuestions()).extracting(QuestionResult.Item::getCriterionId).containsExactly(10L, 10L, 11L, 12L, 11L);
        assertThat(r.getQuestions()).extracting(QuestionResult.Item::getEvidenceId).containsExactly(1L, null, null, null, null);
        assertThat(r.getQuestions()).extracting(QuestionResult.Item::getPriority).containsOnly(2);
    }

    @Test
    void customTypesKeepQuestionsAndOnlyValidLinks() {
        QuestionResult r = result(item("CUSTOM_1", "연결 없음", null, null), item("CUSTOM_1", "남의 평가항목", 99L, 55L),
                item("CUSTOM_1", "근거 2", null, 2L));

        r.validate(Set.of("CUSTOM_1"), Map.of(2L, 11L), Set.of(12L), CLAIM, Set.of("CUSTOM_1"), Set.of(10L, 11L, 12L));

        assertThat(r.getQuestions()).hasSize(3);
        assertThat(r.getQuestions()).extracting(QuestionResult.Item::getCriterionId).containsExactly(null, null, 11L);
    }

    @Test
    void failsWithTheReasonWhenEveryClaimQuestionPointsAtAnUncheckedCriterion() {
        assertThatThrownBy(() -> result(item("DEPTH", "클린 아키텍처를 적용하셨다고 하셨는데", 12L, null))
                .validate(Set.of("DEPTH"), Map.of(1L, 10L), Set.of(12L), CLAIM))
                .isInstanceOf(AiResponseInvalidException.class).hasMessageContaining("DIRECT/INDIRECT");
    }

    @Test
    void failsOnlyWhenNothingIsLeft() {
        assertThatThrownBy(() -> result(item("CHOICE", "x", 10L, null), item("VERIFY", "", 10L, 1L))
                .validate(Set.of("VERIFY"), Map.of(), Set.of(20L), CLAIM))
                .isInstanceOf(AiResponseInvalidException.class).hasMessageContaining("question_type");
    }

    private static QuestionResult result(QuestionResult.Item... items) {
        QuestionResult r = new QuestionResult();
        r.setQuestions(new java.util.ArrayList<>(List.of(items)));
        return r;
    }

    private static QuestionResult.Item item(String type, String content, Long criterionId, Long evidenceId) {
        QuestionResult.Item i = new QuestionResult.Item();
        i.setQuestionType(type);
        i.setContent(content);
        i.setCriterionId(criterionId);
        i.setEvidenceId(evidenceId);
        return i;
    }
}
