package com.example.interview.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.example.interview.ai.AiClient;
import com.example.interview.ai.PromptLoader;
import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.ChatMessage;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.EvidenceType;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.InterviewRecord;
import com.example.interview.domain.Position;
import com.example.interview.domain.QaItem;
import com.example.interview.mapper.ChatMapper;
import com.example.interview.web.dto.CriterionEvidenceView;
import com.example.interview.web.dto.QuestionView;
import com.example.interview.web.dto.RequirementFit;
import com.example.interview.web.dto.ResultReport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI와 대화 (사용자 2026-10-28, 예전 AI 의견 참고를 바꿨다). 지원자마다 대화는 하나다 (면접 전·면접 후를 합쳤다, 사용자).
 * stage는 보낸 단계만 남기고, 그 단계의 자료를 붙인다.
 * AI가 보는 자료는 단계마다 정해져 있고(고르지 않는다), 보낼 때마다 지금 기록으로 새로 만든다.
 * 면접 전: 채용 공고, 개인정보(조건 충족·경력·자격증·가린 이력서), 평가 기준 충족 여부, 면접 질문지.
 * 면접 후: 그 위에 녹음본으로 만든 면접 기록. 면접 전에도 그 기록이 있으면 같이 준다.
 * 이름·연락처·학교·회사 이름은 보내지 않는다 (가린 이력서만 보내고, 프로필은 보내지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    public static final String BEFORE = "BEFORE";
    public static final String AFTER = "AFTER";
    private static final int MAX_MESSAGE = 4000;

    private final ReportService reportService;
    private final CandidateService candidateService;
    private final ChatMapper chatMapper;
    private final AiClient aiClient;
    private final PromptLoader promptLoader;

    public static String stage(String s) {
        return AFTER.equalsIgnoreCase(s) ? AFTER : BEFORE;
    }

    public List<ChatMessage> history(Long candidateId) {
        candidateService.get(candidateId);
        return chatMapper.find(candidateId);
    }

    /** 대화 창 위 「AI가 보고 있는 자료」 칩: key, label, detail, empty */
    public List<Map<String, Object>> sources(Long candidateId, String stage) {
        ResultReport r = reportService.report(candidateId);
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(source("posting", "채용 공고", "", false));
        out.add(source("profile", "개인정보", "가린 이력서 · 경력·학력 조건", r.getCandidate().getMaskedText() == null));
        out.add(source("evidence", "평가 기준 충족 여부", "기준 " + r.getCriteria().size() + "개", r.getCriteria().isEmpty()));
        out.add(source("sheet", "면접 질문지", "답변 " + r.getAnsweredCount() + " / 질문 " + r.getQuestionCount(), r.getSections().isEmpty()));
        // 면접 기록은 면접 후에는 늘, 면접 전에는 녹음본으로 만든 기록이 있을 때만 (사용자 2026-10-28)
        int n = recordSize(r);
        if (AFTER.equals(stage(stage)) || n > 0) {
            out.add(source("record", "면접 기록", "질문 " + n + "개", n == 0));
        }
        return out;
    }

    private static Map<String, Object> source(String key, String label, String detail, boolean empty) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("detail", detail);
        m.put("empty", empty);
        return m;
    }

    /** 보낼 준비가 된 질문: 검사를 마친 뒤 스트림을 시작하기 전에 만든다 (검사 오류는 스트림 전에 400으로 돌려준다) */
    public record Prepared(Long candidateId, String stage, String text, String system, List<Map<String, String>> turns) {
    }

    /** 질문을 보내고 답을 받는다 (조각은 버린다) */
    public ChatMessage send(Long candidateId, String stage, String message) {
        return answer(prepare(candidateId, stage, message), d -> { });
    }

    public Prepared prepare(Long candidateId, String stage, String message) {
        String s = stage(stage);
        String text = message == null ? "" : message.strip();
        if (text.isEmpty()) {
            throw new BusinessException("물어볼 내용을 적어 주세요.");
        }
        if (text.length() > MAX_MESSAGE) {
            throw new BusinessException("한 번에 " + MAX_MESSAGE + "자까지 보낼 수 있습니다.");
        }
        ResultReport r = reportService.report(candidateId);
        if (!r.isFitChecked()) {
            throw new BusinessException("포지션 적합도 검사를 먼저 하세요.");
        }
        List<Map<String, String>> turns = new ArrayList<>();
        for (ChatMessage m : chatMapper.find(candidateId)) {
            turns.add(Map.of("role", "AI".equals(m.getRole()) ? "assistant" : "user", "content", m.getContent()));
        }
        turns.add(Map.of("role", "user", "content", text));
        String system = promptLoader.load("chat") + "\n\n" + material(r, s);
        return new Prepared(candidateId, s, text, system, turns);
    }

    /** 답을 스트림으로 받아 조각마다 onDelta로 넘기고, 다 받으면 질문과 답을 저장한다. AI 호출은 트랜잭션 밖이다 */
    public ChatMessage answer(Prepared p, Consumer<String> onDelta) {
        String reply;
        try {
            reply = aiClient.chatStream(AiPurpose.CHAT, p.candidateId(), p.system(), p.turns(), onDelta);
        } catch (RuntimeException e) {
            log.error("AI 대화 실패 candidateId={}", p.candidateId(), e);
            throw new BusinessException("AI가 답하지 못했습니다. " + e.getMessage());
        }
        save(p.candidateId(), p.stage(), "USER", p.text());
        return save(p.candidateId(), p.stage(), "AI", reply);
    }

    public void reset(Long candidateId) {
        chatMapper.delete(candidateId);
    }

    private ChatMessage save(Long candidateId, String stage, String role, String content) {
        ChatMessage m = new ChatMessage();
        m.setCandidateId(candidateId);
        m.setStage(stage);
        m.setRole(role);
        m.setContent(content);
        chatMapper.insert(m);
        return m;
    }

    /** AI가 보는 자료. 태그 안은 데이터다 */
    static String material(ResultReport r, String stage) {
        Position position = r.getPosition();
        StringBuilder sb = new StringBuilder("# 자료\n\n");
        sb.append("<job_posting>\n").append(position.getPostingText()).append("\n</job_posting>\n\n");

        // 개인정보: 조건 충족 여부, 경력 개월, 자격증, 가린 이력서. 프로필(이름·연락처·학교·회사)은 보내지 않는다
        RequirementFit fit = r.getFit();
        CareerEvalResult eval = r.getCareerEval();
        sb.append("<profile>\n");
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
        CareerEvalResult.Certificates certs = eval == null ? null : eval.getCertificates();
        if (certs != null) {
            sb.append("정보처리기사: ").append(has(certs.getInfoEngineer()))
                    .append(" / SQLD: ").append(has(certs.getSqld())).append('\n');
        }
        sb.append("</profile>\n\n");
        sb.append("<resume>\n").append(r.getCandidate().getMaskedText()).append("\n</resume>\n\n");

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

        sb.append("<interview_sheet>\n");
        sb.append("질문 ").append(r.getQuestionCount()).append("개 중 답변을 적은 질문 ").append(r.getAnsweredCount()).append("개\n");
        r.getSections().forEach(sec -> {
            sb.append("\n[").append(sec.getSection()).append("]\n");
            for (QuestionView v : sec.getQuestions()) {
                InterviewQuestion q = v.getQuestion();
                sb.append("Q. ").append(q.getContent());
                if (v.getCriterionName() != null) {
                    sb.append(" (평가 기준: ").append(v.getCriterionName()).append(')');
                }
                String a = q.getAnswer();
                sb.append("\nA. ").append(a == null || a.isBlank() ? "(답변 기록 없음)" : a).append('\n');
            }
        });
        sb.append("</interview_sheet>");

        if (AFTER.equals(stage) || recordSize(r) > 0) {
            InterviewRecord record = InterviewRecordService.parse(r.getCandidate().getRecordJson());
            sb.append("\n\n<interview_record>\n");
            if (record == null || record.getItems().isEmpty()) {
                sb.append("녹음본으로 만든 면접 기록이 없습니다.\n");
            } else {
                for (QaItem it : record.getItems()) {
                    sb.append(it.isFromCandidate() ? "[지원자 질문] " : "").append("Q. ").append(it.getQuestion())
                            .append("\nA. ").append(it.getAnswer()).append('\n');
                }
            }
            sb.append("</interview_record>");
        }
        return sb.toString();
    }

    private static int recordSize(ResultReport r) {
        InterviewRecord record = InterviewRecordService.parse(r.getCandidate().getRecordJson());
        return record == null ? 0 : record.getItems().size();
    }

    private static String orNone(String s) {
        return s == null || s.isBlank() ? "공고에 없음" : s;
    }

    private static String met(Boolean value) {
        return value == null ? "판단 없음" : value ? "충족" : "미충족";
    }

    private static String has(Boolean b) {
        return b == null ? "모름" : b ? "있음" : "없음";
    }

    private static String judgment(EvidenceType type) {
        return switch (type) {
            case DIRECT -> "충족";
            case INDIRECT -> "부분 충족";
            case NONE -> "미확인";
        };
    }

    private static String months(int m) {
        return (m / 12 > 0 ? m / 12 + "년 " : "") + m % 12 + "개월";
    }
}
