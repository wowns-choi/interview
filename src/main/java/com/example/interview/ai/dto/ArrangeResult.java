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
     * 없는 id와 중복만 오류로 본다. 빠진 질문은 QuestionSheetAssembler가 원래 자리 근처에 채운다.
     *
     * @param templateIds 보낸 공통 질문 id
     * @param questionCount 보낸 추출 질문 수 (question_id는 1부터)
     */
    public void validate(Collection<Long> templateIds, int questionCount) {
        if (sections == null || sections.isEmpty()) {
            throw new AiResponseInvalidException("sections 배열이 비어 있습니다.");
        }
        List<String> errors = new ArrayList<>();
        Set<Long> templates = new HashSet<>();
        Set<Long> questions = new HashSet<>();
        for (int s = 0; s < sections.size(); s++) {
            Section section = sections.get(s);
            if (section == null || section.name == null || section.name.isBlank()) {
                errors.add("sections[" + s + "].name 누락");
                continue;
            }
            if (section.items == null || section.items.isEmpty()) {
                errors.add("sections[" + s + "].items가 비어 있음");
                continue;
            }
            for (Item item : section.items) {
                if (item == null || (item.templateId == null) == (item.questionId == null)) {
                    errors.add("sections[" + s + "]의 항목은 template_id와 question_id 중 하나만 가져야 함");
                } else if (item.templateId != null) {
                    if (!templateIds.contains(item.templateId) || !templates.add(item.templateId)) {
                        errors.add("template_id " + item.templateId + "가 없거나 두 번 이상 나옴");
                    }
                } else if (item.questionId < 1 || item.questionId > questionCount || !questions.add(item.questionId)) {
                    errors.add("question_id " + item.questionId + "가 없거나 두 번 이상 나옴");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new AiResponseInvalidException(String.join("; ", errors));
        }
    }
}
