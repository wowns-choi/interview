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
    void keepsQuestionsWithoutALinkAndLinksWhatIsValid() {
        QuestionResult r = result(
                item("VERIFY", "근거 1 질문", 99L, 1L),
                item("DEPTH", "근거 번호 없음, 평가항목만", 10L, null),
                item("DEPTH", "없는 근거, 없는 평가항목", 77L, 7L),
                item("EXPERIENCE", "Kafka 해보셨나요?", 20L, 3L),
                item("CHOICE", "안 고른 유형", 10L, 1L),
                item("VERIFY", " ", 10L, 1L));

        r.validate(Set.of("VERIFY", "DEPTH", "EXPERIENCE"), Map.of(1L, 10L), Set.of(20L), CLAIM);

        // 로컬 AI가 근거 번호를 비워도 질문은 남긴다 (사용자 2026-11-09)
        assertThat(r.getQuestions()).extracting(QuestionResult.Item::getContent)
                .containsExactly("근거 1 질문", "근거 번호 없음, 평가항목만", "없는 근거, 없는 평가항목", "Kafka 해보셨나요?");
        assertThat(r.getQuestions()).extracting(QuestionResult.Item::getCriterionId)
                .as("근거가 맞으면 근거의 평가항목, 아니면 AI가 적은 평가항목, 둘 다 틀리면 없음")
                .containsExactly(10L, 10L, null, 20L);
        assertThat(r.getQuestions()).extracting(QuestionResult.Item::getEvidenceId).containsExactly(1L, null, null, null);
        assertThat(r.getQuestions()).extracting(QuestionResult.Item::getPriority).containsOnly(2);
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
