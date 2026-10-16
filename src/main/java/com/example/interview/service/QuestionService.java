package com.example.interview.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.example.interview.domain.Position;
import com.example.interview.domain.QuestionSection;
import com.example.interview.domain.QuestionTemplate;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 호출 ③ 두 번: 평가항목 검증 질문 추출 → 공통 질문과 합쳐 순서·섹션 정하기(공통 질문 개인화 포함).
 * 근거 id는 목록 순번(1부터), 추출 질문 id도 목록 순번(1부터)으로 보낸다.
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final AiClient aiClient;
    private final PromptLoader promptLoader;

    public QuestionResult generate(Position position, Candidate candidate, List<EvalCriterion> criteria,
            List<Evidence> evidences) {
        StringBuilder sb = new StringBuilder();
        sb.append("<job_posting>\n").append(position.getPostingText()).append("\n</job_posting>\n\n");
        sb.append("<criteria>\n").append(toJson(map(criteria, c -> new CriterionInput(c.getId(), c.getName(),
                c.getDescription(), c.getRequirement().name(), c.getCategory().name())))).append("\n</criteria>\n\n");
        sb.append("<resume>\n").append(candidate.getMaskedText()).append("\n</resume>\n\n");
        sb.append("<evidence>\n").append(toJson(evidenceInput(evidences))).append("\n</evidence>\n\n");
        sb.append("<career_level>")
                .append(candidate.getCareerLevel() == null ? "미기재 (이력서 내용으로 판단)" : candidate.getCareerLevel())
                .append("</career_level>");

        return aiClient.call(AiPurpose.QUESTION, candidate.getId(), promptLoader.load("questions"),
                sb.toString(), QuestionResult.class, QuestionResult::validate);
    }

    public ArrangeResult arrange(Candidate candidate, List<EvalCriterion> criteria, List<Evidence> evidences,
            List<QuestionSection> sections, List<QuestionTemplate> templates, List<QuestionResult.Item> questions) {
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
            QuestionResult.Item q = questions.get(i);
            extracted.add(new ExtractedInput((long) (i + 1), q.getQuestionType(), q.getContent(),
                    criterionNames.get(q.getCriterionId()), q.getPriority()));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<common_questions>\n").append(toJson(common)).append("\n</common_questions>\n\n");
        sb.append("<questions>\n").append(toJson(extracted)).append("\n</questions>\n\n");
        sb.append("<resume>\n").append(candidate.getMaskedText()).append("\n</resume>\n\n");
        sb.append("<evidence>\n").append(toJson(evidenceInput(evidences))).append("\n</evidence>");

        List<Long> templateIds = templates.stream().map(QuestionTemplate::getId).toList();
        return aiClient.call(AiPurpose.ARRANGE, candidate.getId(), promptLoader.load("arrange"),
                sb.toString(), ArrangeResult.class, r -> r.validate(templateIds, questions.size()));
    }

    private List<Object> evidenceInput(List<Evidence> evidences) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < evidences.size(); i++) {
            Evidence e = evidences.get(i);
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

    record CriterionInput(Long criterion_id, String name, String description, String requirement, String category) {
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
