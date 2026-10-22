package com.example.interview.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.interview.ai.dto.ArrangeResult;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.QuestionOrigin;
import com.example.interview.domain.QuestionSection;
import com.example.interview.domain.QuestionTemplate;

/**
 * 배치 응답(③-2) 순서대로 질문지를 만든다. AI를 쓰지 않는 순수 로직이다.
 * 추출해 둔 질문(행)은 새로 만들지 않고 섹션만 정해 그대로 넣는다 (id·답변 유지).
 * 공통 질문은 원문 그대로 COMMON_ORIGINAL (COMMON_PERSONALIZED는 예전 질문지에만 있다).
 * AI가 빠뜨린 질문은 원래 자리 근처에 채운다: 공통 질문은 같은 섹션의 앞뒤 질문 옆에, 추출 질문은 같은 평가항목 질문 뒤에.
 */
@Component
public class QuestionSheetAssembler {

    static final String LEFTOVER_SECTION = "기타";
    static final String ADDED_SECTION = "추가 질문";

    /**
     * @param templates         공통 질문 (섹션 순서 → 질문 순서로 정렬된 것)
     * @param questions         추출해 둔 질문 (question_id = 목록 순번, 1부터)
     * @return 질문 목록. 공통 질문은 새 행(id 없음), 추출 질문은 받은 행 그대로
     */
    public List<InterviewQuestion> assemble(Long candidateId, List<QuestionSection> sections,
            List<QuestionTemplate> templates, List<InterviewQuestion> questions, ArrangeResult arrangement) {
        Map<Long, QuestionTemplate> templateById = new LinkedHashMap<>();
        templates.forEach(t -> templateById.put(t.getId(), t));
        Map<Long, InterviewQuestion> placedTemplates = new HashMap<>();
        Map<Integer, InterviewQuestion> placedQuestions = new HashMap<>();
        List<InterviewQuestion> sheet = new ArrayList<>();

        for (ArrangeResult.Section section : arrangement.getSections()) {
            String name = section.getName() == null || section.getName().isBlank()
                    ? LEFTOVER_SECTION : section.getName().strip();
            for (ArrangeResult.Item item : section.getItems()) {
                if (item.getTemplateId() != null) {
                    QuestionTemplate t = templateById.get(item.getTemplateId());
                    if (t != null && !placedTemplates.containsKey(t.getId())) {
                        InterviewQuestion q = common(candidateId, name, t);
                        placedTemplates.put(t.getId(), q);
                        sheet.add(q);
                    }
                } else if (item.getQuestionId() != null) {
                    int index = item.getQuestionId().intValue() - 1;
                    if (index >= 0 && index < questions.size() && !placedQuestions.containsKey(index)) {
                        InterviewQuestion q = place(questions.get(index), name);
                        placedQuestions.put(index, q);
                        sheet.add(q);
                    }
                }
            }
        }

        fillMissingTemplates(sheet, candidateId, sections, templates, placedTemplates);
        fillMissingQuestions(sheet, questions, placedQuestions, LEFTOVER_SECTION);
        return renumber(sheet);
    }

    /**
     * 이미 있는 질문지는 그대로 두고 새로 추출한 질문만 넣는다: 같은 평가항목 질문 뒤, 없으면 마지막 추출 질문 뒤,
     * 그것도 없으면 맨 끝 "추가 질문" 섹션.
     */
    public List<InterviewQuestion> insert(List<InterviewQuestion> sheet, List<InterviewQuestion> questions) {
        List<InterviewQuestion> merged = new ArrayList<>(sheet);
        fillMissingQuestions(merged, questions, new HashMap<>(), ADDED_SECTION);
        return renumber(merged);
    }

    private static List<InterviewQuestion> renumber(List<InterviewQuestion> sheet) {
        int order = 1;
        for (InterviewQuestion q : sheet) {
            q.setSortOrder(order++);
        }
        return sheet;
    }

    /**
     * 같은 섹션의 바로 앞 질문 뒤(없으면 바로 뒤 질문 앞)에 그 질문의 섹션 이름으로 넣는다.
     * 섹션 전체가 빠졌으면 원래 섹션 이름으로, 앞 섹션 질문들 뒤에 넣는다.
     */
    private static void fillMissingTemplates(List<InterviewQuestion> sheet, Long candidateId,
            List<QuestionSection> sections, List<QuestionTemplate> templates, Map<Long, InterviewQuestion> placed) {
        Map<Long, String> sectionNames = new HashMap<>();
        sections.forEach(s -> sectionNames.put(s.getId(), s.getName()));

        for (int i = 0; i < templates.size(); i++) {
            QuestionTemplate t = templates.get(i);
            if (placed.containsKey(t.getId())) {
                continue;
            }
            InterviewQuestion before = neighbor(templates, placed, i, -1, true);
            InterviewQuestion after = before == null ? neighbor(templates, placed, i, 1, true) : null;
            int at;
            String name;
            if (before != null) {
                at = indexOf(sheet, before) + 1;
                name = before.getSection();
            } else if (after != null) {
                at = indexOf(sheet, after);
                name = after.getSection();
            } else {
                InterviewQuestion earlier = neighbor(templates, placed, i, -1, false);
                at = earlier == null ? 0 : lastIndexOfSection(sheet, earlier) + 1;
                name = sectionNames.getOrDefault(t.getSectionId(), LEFTOVER_SECTION);
            }
            InterviewQuestion q = common(candidateId, name, t);
            placed.put(t.getId(), q);
            sheet.add(at, q);
        }
    }

    /** 같은 평가항목을 다루는 질문 뒤에, 없으면 마지막 추출 질문 뒤에, 그것도 없으면 맨 끝 leftover 섹션에 넣는다. */
    private static void fillMissingQuestions(List<InterviewQuestion> sheet, List<InterviewQuestion> questions,
            Map<Integer, InterviewQuestion> placed, String leftover) {
        for (int i = 0; i < questions.size(); i++) {
            if (placed.containsKey(i)) {
                continue;
            }
            InterviewQuestion item = questions.get(i);
            InterviewQuestion anchor = null;
            for (int j = sheet.size() - 1; j >= 0 && anchor == null; j--) {
                InterviewQuestion q = sheet.get(j);
                if (q.getOrigin() == QuestionOrigin.CUSTOM && item.getCriterionId() != null
                        && Objects.equals(q.getCriterionId(), item.getCriterionId())) {
                    anchor = q;
                }
            }
            for (int j = sheet.size() - 1; j >= 0 && anchor == null; j--) {
                if (sheet.get(j).getOrigin() == QuestionOrigin.CUSTOM) {
                    anchor = sheet.get(j);
                }
            }
            InterviewQuestion q = place(item, anchor == null ? leftover : anchor.getSection());
            placed.put(i, q);
            sheet.add(anchor == null ? sheet.size() : indexOf(sheet, anchor) + 1, q);
        }
    }

    /** templates[from]에서 step 방향으로 가며 이미 놓인 가장 가까운 질문. sameSection이면 같은 섹션 안에서만 찾는다. */
    private static InterviewQuestion neighbor(List<QuestionTemplate> templates, Map<Long, InterviewQuestion> placed,
            int from, int step, boolean sameSection) {
        Long sectionId = templates.get(from).getSectionId();
        for (int j = from + step; j >= 0 && j < templates.size(); j += step) {
            QuestionTemplate t = templates.get(j);
            if (sameSection && !Objects.equals(t.getSectionId(), sectionId)) {
                break;
            }
            if (placed.containsKey(t.getId())) {
                return placed.get(t.getId());
            }
        }
        return null;
    }

    private static int lastIndexOfSection(List<InterviewQuestion> sheet, InterviewQuestion q) {
        int i = indexOf(sheet, q);
        while (i + 1 < sheet.size() && sheet.get(i + 1).getSection().equals(q.getSection())) {
            i++;
        }
        return i;
    }

    private static int indexOf(List<InterviewQuestion> sheet, InterviewQuestion q) {
        for (int i = 0; i < sheet.size(); i++) {
            if (sheet.get(i) == q) {
                return i;
            }
        }
        return sheet.size() - 1;
    }

    /** 공통 질문은 원문 그대로 넣는다 (지원자에 맞게 고치던 기능은 사용자가 없앴다) */
    private static InterviewQuestion common(Long candidateId, String section, QuestionTemplate t) {
        InterviewQuestion q = newQuestion(candidateId, section);
        q.setTemplateId(t.getId());
        q.setOrigin(QuestionOrigin.COMMON_ORIGINAL);
        q.setContent(t.getContent());
        return q;
    }

    private static InterviewQuestion place(InterviewQuestion q, String section) {
        q.setSection(trim(section));
        q.setMerged(true);
        return q;
    }

    private static InterviewQuestion newQuestion(Long candidateId, String sectionName) {
        InterviewQuestion q = new InterviewQuestion();
        q.setCandidateId(candidateId);
        q.setSection(trim(sectionName));
        q.setResumeAnsweredYn("N");
        return q;
    }

    private static String trim(String section) {
        return section.length() > 100 ? section.substring(0, 100) : section;
    }

}
