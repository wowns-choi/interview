package com.example.interview.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.interview.ai.AiClient;
import com.example.interview.ai.PromptLoader;
import com.example.interview.ai.dto.ArrangeResult;
import com.example.interview.ai.dto.QuestionResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.EvidenceType;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.Position;
import com.example.interview.domain.QuestionSection;
import com.example.interview.domain.QuestionTemplate;
import com.example.interview.domain.QuestionType;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 호출 ③ 두 가지: 고른 유형의 질문 추출(여러 번 할 수 있다) / 추출해 둔 질문을 공통 질문과 합쳐 순서·섹션 정하기(질문 내용은 그대로).
 * 순서 정하기에는 질문만 보내고 이력서·근거는 보내지 않는다.
 * 근거 id는 목록 순번(1부터), 추출 질문 id도 목록 순번(1부터)으로 보낸다.
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final AiClient aiClient;
    private final PromptLoader promptLoader;

    /**
     * @param existing 이미 추출한 질문 (겹치지 않게 보낸다)
     */
    public QuestionResult generate(Position position, Candidate candidate, List<EvalCriterion> criteria,
            List<Evidence> evidences, Collection<QuestionType> types, List<InterviewQuestion> existing) {
        Map<Long, Long> claimRefs = claimRefs(evidences);
        Set<Long> experience = experienceCriteria(criteria, evidences);
        Set<String> typeNames = types.stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> claimTypes = types.stream().filter(QuestionType::isClaim).map(Enum::name).collect(Collectors.toSet());

        StringBuilder sb = new StringBuilder();
        sb.append("<question_types>").append(String.join(", ", typeNames)).append("</question_types>\n\n");
        sb.append("<job_posting>\n").append(position.getPostingText()).append("\n</job_posting>\n\n");
        sb.append("<criteria>\n").append(toJson(map(criteria, c -> new CriterionInput(c.getId(), c.getName(),
                c.getDescription(), c.getCategory().name())))).append("\n</criteria>\n\n");
        if (!claimTypes.isEmpty()) {
            sb.append("<resume>\n").append(candidate.getMaskedText()).append("\n</resume>\n\n");
            sb.append("<evidence>\n").append(toJson(evidenceInput(evidences))).append("\n</evidence>\n\n");
        }
        if (types.contains(QuestionType.EXPERIENCE)) {
            sb.append("<experience_criteria>\n").append(toJson(criteria.stream()
                    .filter(c -> experience.contains(c.getId())).map(c -> (Object) c.getId()).toList()))
                    .append("\n</experience_criteria>\n\n");
        }
        sb.append("<existing_questions>\n").append(toJson(existing.stream()
                .filter(q -> q.getQuestionType() != null).map(q -> (Object) q.getContent()).toList()))
                .append("\n</existing_questions>\n\n");
        sb.append("<career_level>")
                .append(candidate.getCareerLevel() == null ? "미기재 (이력서 내용으로 판단)" : candidate.getCareerLevel())
                .append("</career_level>");

        return aiClient.call(AiPurpose.QUESTION, candidate.getId(), promptLoader.load("questions"),
                sb.toString(), QuestionResult.class, r -> r.validate(typeNames, claimRefs, experience, claimTypes));
    }

    /** 근거 참조값(1부터) → 평가항목 id. 근거 질문이 기댈 수 있는 직접·간접 근거만 */
    static Map<Long, Long> claimRefs(List<Evidence> evidences) {
        Map<Long, Long> refs = new LinkedHashMap<>();
        for (int i = 0; i < evidences.size(); i++) {
            Evidence e = evidences.get(i);
            if (e.getEvidenceType() != EvidenceType.NONE) {
                refs.put((long) (i + 1), e.getCriterionId());
            }
        }
        return refs;
    }

    /** "해본 적 있어?"로 물을 평가항목: 직접 근거가 없는 것 */
    static Set<Long> experienceCriteria(List<EvalCriterion> criteria, List<Evidence> evidences) {
        Set<Long> direct = evidences.stream().filter(e -> e.getEvidenceType() == EvidenceType.DIRECT)
                .map(Evidence::getCriterionId).collect(Collectors.toSet());
        return criteria.stream().map(EvalCriterion::getId).filter(id -> !direct.contains(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * @param questions 추출해 둔 질문 (question_id = 목록 순번, 1부터)
     */
    public ArrangeResult arrange(Candidate candidate, List<EvalCriterion> criteria, List<QuestionSection> sections,
            List<QuestionTemplate> templates, List<InterviewQuestion> questions) {
        Map<Long, List<QuestionTemplate>> bySection = templates.stream()
                .collect(Collectors.groupingBy(QuestionTemplate::getSectionId));
        List<Object> common = new ArrayList<>();
        for (QuestionSection s : sections) {
            List<QuestionTemplate> list = bySection.getOrDefault(s.getId(), List.of());
            if (!list.isEmpty()) {
                common.add(new CommonSectionInput(s.getName(),
                        map(list, t -> new CommonInput(t.getId(), t.getContent()))));
            }
        }
        Map<Long, String> criterionNames = criteria.stream()
                .collect(Collectors.toMap(EvalCriterion::getId, EvalCriterion::getName));
        List<Object> extracted = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            InterviewQuestion q = questions.get(i);
            extracted.add(new ExtractedInput((long) (i + 1),
                    q.getQuestionType() == null ? null : q.getQuestionType().name(), q.getContent(),
                    criterionNames.get(q.getCriterionId()), q.getPriority()));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<common_questions>\n").append(toJson(common)).append("\n</common_questions>\n\n");
        sb.append("<questions>\n").append(toJson(extracted)).append("\n</questions>");

        List<Long> templateIds = templates.stream().map(QuestionTemplate::getId).toList();
        return aiClient.call(AiPurpose.ARRANGE, candidate.getId(), promptLoader.load("arrange"),
                sb.toString(), ArrangeResult.class, r -> r.validate(templateIds, questions.size()));
    }

    /** 근거 없음(NONE) 행은 빼고 보낸다. 번호는 전체 목록 순번 그대로 */
    private List<Object> evidenceInput(List<Evidence> evidences) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < evidences.size(); i++) {
            Evidence e = evidences.get(i);
            if (e.getEvidenceType() == EvidenceType.NONE) {
                continue;
            }
            list.add(new EvidenceInput((long) (i + 1), e.getCriterionId(), e.getEvidenceType().name(), e.getQuote(),
                    e.getInterpretation(), "Y".equals(e.getVerifiedYn()), e.getInterviewPoint()));
        }
        return list;
    }

    private static <T> List<Object> map(List<T> items, Function<T, Object> f) {
        return items.stream().map(f).toList();
    }

    private static String toJson(Object value) {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    record CriterionInput(Long criterion_id, String name, String description, String category) {
    }

    record EvidenceInput(Long evidence_id, Long criterion_id, String type, String quote, String interpretation,
            boolean quote_verified, String interview_point) {
    }

    record CommonSectionInput(String section, List<Object> questions) {
    }

    record CommonInput(Long template_id, String content) {
    }

    record ExtractedInput(Long question_id, String question_type, String content, String criterion, Integer priority) {
    }
}
