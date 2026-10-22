package com.example.interview.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.interview.ai.AiClient;
import com.example.interview.ai.PromptLoader;
import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.ai.dto.OpinionResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.EvidenceType;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.Position;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.web.dto.CriterionEvidenceView;
import com.example.interview.web.dto.QuestionView;
import com.example.interview.web.dto.RequirementFit;
import com.example.interview.web.dto.ResultReport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 의견 참고 (사용자 요청): 판단 자료(공고, 조건 충족 여부, 평가항목별 근거, 면접 답변, 가린 이력서)를 주고
 * 뽑는다면·안 뽑는다면 각각 그 이유를 묻는다. 결론이나 점수는 받지 않는다.
 * 학력은 조건 충족 여부만 보내고 학교·전공은 보내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpinionService {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter ASKED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReportService reportService;
    private final CandidateMapper candidateMapper;
    private final AiClient aiClient;
    private final PromptLoader promptLoader;

    public void ask(Long candidateId) {
        ResultReport r = reportService.report(candidateId);
        if (!r.isFitChecked()) {
            throw new BusinessException("포지션 적합도 검사를 먼저 하세요.");
        }
        OpinionResult result;
        try {
            result = aiClient.call(AiPurpose.OPINION, candidateId, promptLoader.load("opinion"), input(r),
                    OpinionResult.class, OpinionResult::validate);
        } catch (RuntimeException e) {
            log.error("AI 의견 실패 candidateId={}", candidateId, e);
            throw new BusinessException("AI 의견을 받지 못했습니다. " + e.getMessage());
        }
        result.setAskedAt(LocalDateTime.now().format(ASKED_AT));
        candidateMapper.updateOpinion(candidateId, JSON.writeValueAsString(result));
    }

    static String input(ResultReport r) {
        Position position = r.getPosition();
        StringBuilder sb = new StringBuilder();
        sb.append("<job_posting>\n").append(position.getPostingText()).append("\n</job_posting>\n\n");

        RequirementFit fit = r.getFit();
        CareerEvalResult eval = r.getCareerEval();
        sb.append("<conditions>\n");
        sb.append("경력 조건: ").append(orNone(position.getRequiredExperience()));
        if (eval != null && eval.getTotalMonths() != null) {
            sb.append(" / 지원자 총 경력 ").append(months(eval.getTotalMonths()));
            if (eval.getRelevantMonths() != null) {
                sb.append(", 포지션 관련 경력 ").append(months(eval.getRelevantMonths()));
            }
        }
        sb.append(" / ").append(met(fit.getCareer())).append('\n');
        sb.append("학력 조건: ").append(orNone(eval == null ? null : eval.getRequiredEducation()))
                .append(" / ").append(met(fit.getEducation())).append('\n');
        sb.append("</conditions>\n\n");

        sb.append("<criteria>\n");
        int no = 1;
        for (CriterionEvidenceView c : r.getCriteria()) {
            sb.append(no++).append(". ").append(c.getCriterion().getName());
            if (c.getCriterion().getDescription() != null && !c.getCriterion().getDescription().isBlank()) {
                sb.append(" (").append(c.getCriterion().getDescription()).append(')');
            }
            sb.append(" → ").append(c.isChecked() ? judgment(c.getMatch()) : "검사 전").append('\n');
            for (Evidence e : c.getQuotedEvidences()) {
                sb.append("   - [").append(judgment(e.getEvidenceType())).append("] \"").append(e.getQuote()).append('"');
                if (!"Y".equals(e.getVerifiedYn())) {
                    sb.append(" (이력서 원문에서 확인되지 않은 문장)");
                }
                if (e.getInterpretation() != null) {
                    sb.append(" / 해석: ").append(e.getInterpretation());
                }
                sb.append('\n');
            }
            if (c.getMatch() == EvidenceType.NONE && c.getNoneNote() != null) {
                sb.append("   - ").append(c.getNoneNote()).append('\n');
            }
        }
        sb.append("</criteria>\n\n");

        List<QuestionView> answered = r.getSections().stream().flatMap(s -> s.getQuestions().stream())
                .filter(q -> q.getQuestion().getAnswer() != null && !q.getQuestion().getAnswer().isBlank()).toList();
        sb.append("<interview>\n");
        sb.append("답변을 기록한 질문 ").append(answered.size()).append("개 / 질문지 ").append(r.getQuestionCount()).append("개\n");
        for (QuestionView v : answered) {
            InterviewQuestion q = v.getQuestion();
            sb.append("Q. ").append(q.getContent());
            if (v.getCriterionName() != null) {
                sb.append(" (평가항목: ").append(v.getCriterionName()).append(')');
            }
            sb.append("\nA. ").append(q.getAnswer()).append("\n\n");
        }
        sb.append("</interview>\n\n");

        sb.append("<resume>\n").append(r.getCandidate().getMaskedText()).append("\n</resume>");
        return sb.toString();
    }

    private static String orNone(String s) {
        return s == null || s.isBlank() ? "공고에 없음" : s;
    }

    private static String met(Boolean value) {
        return value == null ? "판단 없음" : value ? "충족" : "미충족";
    }

    private static String judgment(EvidenceType type) {
        return switch (type) {
            case DIRECT -> "직접 근거";
            case INDIRECT -> "간접 근거";
            case NONE -> "근거 없음";
        };
    }

    private static String months(int m) {
        return (m / 12 > 0 ? m / 12 + "년 " : "") + m % 12 + "개월";
    }
}
