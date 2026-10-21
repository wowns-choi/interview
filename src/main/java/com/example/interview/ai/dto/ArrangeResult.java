package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.interview.ai.AiResponseInvalidException;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** 호출 ③-2 배치 응답: 공통 질문과 추출한 질문을 섹션별 면접 순서로 */
@Data
public class ArrangeResult {

    private List<Section> sections;

    @Data
    public static class Section {
        private String name;
        private List<Item> items;
    }

    /** template_id(공통 질문)와 question_id(추출한 질문) 중 하나만 있다 */
    @Data
    public static class Item {
        @JsonProperty("template_id")
        private Long templateId;
        @JsonProperty("question_id")
        private Long questionId;
        private Boolean personalized;
        private String content;
        @JsonProperty("evidence_id")
        private Long evidenceId;
        @JsonProperty("resume_answered")
        private Boolean resumeAnswered;
    }

    /**
     * 없는 id, 중복, 잘못된 항목은 버린다 (gpt-4o-mini가 없는 question_id를 같은 자리에 되풀이해 재시도도 실패하곤 했다).
     * 버린 질문과 빠진 질문은 QuestionSheetAssembler가 원래 자리 근처에 채운다. 남는 섹션이 없을 때만 실패.
     *
     * @param templateIds 보낸 공통 질문 id
     * @param questionCount 보낸 추출 질문 수 (question_id는 1부터)
     */
    public void validate(Collection<Long> templateIds, int questionCount) {
        if (sections == null || sections.isEmpty()) {
            throw new AiResponseInvalidException("sections 배열이 비어 있습니다.");
        }
        Set<Long> templates = new HashSet<>();
        Set<Long> questions = new HashSet<>();
        List<Section> kept = new ArrayList<>();
        for (Section section : sections) {
            if (section == null || section.name == null || section.name.isBlank() || section.items == null) {
                continue;
            }
            List<Item> items = new ArrayList<>();
            for (Item item : section.items) {
                if (item == null || (item.templateId == null) == (item.questionId == null)) {
                    continue;
                }
                boolean ok = item.templateId != null
                        ? templateIds.contains(item.templateId) && templates.add(item.templateId)
                        : item.questionId >= 1 && item.questionId <= questionCount && questions.add(item.questionId);
                if (ok) {
                    items.add(item);
                }
            }
            if (!items.isEmpty()) {
                section.setItems(items);
                kept.add(section);
            }
        }
        if (kept.isEmpty()) {
            throw new AiResponseInvalidException("sections에 쓸 수 있는 항목이 없습니다.");
        }
        sections = kept;
    }
}
