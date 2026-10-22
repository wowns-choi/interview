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
    void keepsOnlyQuestionsThatPointAtTheRightThing() {
        QuestionResult r = result(
                item("VERIFY", "근거 1 질문", 99L, 1L),
                item("DEPTH", "없는 근거", 10L, 7L),
                item("EXPERIENCE", "직접 근거 있는 항목", 10L, null),
                item("EXPERIENCE", "Kafka 해보셨나요?", 20L, 3L),
                item("CHOICE", "안 고른 유형", 10L, 1L));

        r.validate(Set.of("VERIFY", "DEPTH", "EXPERIENCE"), Map.of(1L, 10L), Set.of(20L), CLAIM);

        assertThat(r.getQuestions()).extracting(QuestionResult.Item::getContent).containsExactly("근거 1 질문", "Kafka 해보셨나요?");
        assertThat(r.getQuestions().get(0).getCriterionId()).as("평가항목은 근거의 것으로").isEqualTo(10L);
        assertThat(r.getQuestions().get(1).getEvidenceId()).isNull();
        assertThat(r.getQuestions()).extracting(QuestionResult.Item::getPriority).containsOnly(2);
    }

    @Test
    void failsOnlyWhenNothingIsLeft() {
        assertThatThrownBy(() -> result(item("EXPERIENCE", "x", 10L, null)).validate(ALL, Map.of(), Set.of(20L), CLAIM))
                .isInstanceOf(AiResponseInvalidException.class).hasMessageContaining("experience_criteria");
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
