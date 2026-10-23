package com.example.interview.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.example.interview.ai.AiClient;
import com.example.interview.ai.PromptLoader;
import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.ai.dto.ReportInfoResult;
import com.example.interview.ai.dto.ReportSortResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateProfile;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.domain.InterviewRecord;
import com.example.interview.domain.InterviewReport;
import com.example.interview.domain.QaItem;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.mapper.InterviewQuestionMapper;
import com.example.interview.web.dto.CareerGauge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 합격자 보고서 (사용자 요청). 처음 만들 때 이력서 정보·적합도 검사 결과·면접 기록(또는 질문지)을 복사해 오고,
 * 파견 가능 여부·입사 가능 시기·희망연봉은 질문지 답변과 녹음본에서 GPT가 찾는다 (다르면 녹음본이 우선. 희망연봉도 보낸다: 사용자 결정).
 * 그 뒤로는 보고서만 고치고 원래 기록은 그대로 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewReportService {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final CandidateService candidateService;
    private final CandidateMapper candidateMapper;
    private final InterviewQuestionMapper questionMapper;
    private final AiClient aiClient;
    private final PromptLoader promptLoader;

    public InterviewReport get(Long candidateId) {
        return parse(candidateService.get(candidateId).getReportJson());
    }

    /** 새로 만든다 (이미 있으면 덮어쓴다). 질문과 답변은 면접 기록이 있으면 그것, 없으면 질문지에서 */
    public InterviewReport create(Long candidateId) {
        Candidate c = candidateService.get(candidateId);
        InterviewRecord record = InterviewRecordService.parse(c.getRecordJson());
        List<InterviewQuestion> sheet = answeredSheet(candidateId);

        InterviewReport report = new InterviewReport();
        report.setReportDate(LocalDate.now().toString());
        report.setInterviewDate(c.getInterviewDate() == null ? "" : c.getInterviewDate().toString());
        boolean fromRecord = record != null && !record.getItems().isEmpty();
        report.setQaSource(fromRecord || sheet.isEmpty() ? InterviewReport.RECORD : InterviewReport.SHEET);
        report.setQa(qa(report.getQaSource(), record, sheet));
        report.setInfo(info(c, ReportService.parseProfile(c.getProfileJson()), CareerEvalService.parse(c.getCareerEvalJson()),
                interviewInfo(c, record, sheet), LocalDate.now()));
        report.getOpinions().add(new InterviewReport.Opinion());
        save(candidateId, report);
        return report;
    }

    public void save(Long candidateId, InterviewReport report) {
        candidateService.get(candidateId);
        report.clean();
        candidateMapper.updateReport(candidateId, JSON.writeValueAsString(report));
    }

    /** 질문과 답변을 다른 곳(면접 기록·질문지)에서 다시 가져온다. 보고서에서 고친 질문과 답변은 사라진다 */
    public void useSource(Long candidateId, String source) {
        InterviewReport report = require(candidateId);
        Candidate c = candidateService.get(candidateId);
        String s = InterviewReport.SHEET.equals(source) ? InterviewReport.SHEET : InterviewReport.RECORD;
        List<QaItem> qa = qa(s, InterviewRecordService.parse(c.getRecordJson()), answeredSheet(candidateId));
        if (qa.isEmpty()) {
            throw new BusinessException(InterviewReport.SHEET.equals(s) ? "면접 질문지에 적은 답변이 없습니다."
                    : "면접 기록이 없습니다. 면접 질문지의 녹음본 탭에서 만드세요.");
        }
        report.setQaSource(s);
        report.setQa(qa);
        save(candidateId, report);
    }

    /**
     * 지원자 정보 다시 찾기: 파견 가능 여부·입사 가능 시기·희망연봉만 녹음본과 질문지 답변에서 다시 찾는다.
     * 찾은 값만 바꾸고(못 찾은 칸은 그대로), 지운 행은 끝에 다시 붙인다. 다른 내용은 건드리지 않는다.
     * @return 채운 칸 수
     */
    public int refreshInfo(Long candidateId) {
        InterviewReport report = require(candidateId);
        Candidate c = candidateService.get(candidateId);
        InterviewRecord record = InterviewRecordService.parse(c.getRecordJson());
        List<InterviewQuestion> sheet = answeredSheet(candidateId);
        if (sheet.isEmpty() && record == null && (c.getTranscript() == null || c.getTranscript().isBlank())) {
            throw new BusinessException("찾을 면접 질문-답변이 없습니다. 면접 기록을 만들거나 질문지에 답변을 적으세요.");
        }
        ReportInfoResult found = interviewInfo(c, record, sheet);
        if (found == null) {
            throw new BusinessException("지원자 정보를 찾지 못했습니다. 잠시 뒤 다시 시도하세요.");
        }
        int filled = 0;
        filled += setInfo(report, "파견 가능 여부", found.getDispatch());
        filled += setInfo(report, "입사 가능 시기", found.getStartDate());
        filled += setInfo(report, "희망연봉", found.getDesiredSalary());
        save(candidateId, report);
        return filled;
    }

    private static int setInfo(InterviewReport report, String label, String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        InterviewReport.InfoRow row = report.getInfo().stream().filter(r -> label.equals(r.getLabel())).findFirst().orElse(null);
        if (row == null) {
            report.getInfo().add(InterviewReport.InfoRow.of(label, value));
        } else {
            row.setValue(value);
        }
        return 1;
    }

    /** 보고서를 받는 사람이 읽기 편한 순서로 GPT가 정렬한다. 면접관 질문과 지원자 질문은 따로 묶는다 */
    public void sort(Long candidateId) {
        InterviewReport report = require(candidateId);
        List<QaItem> qa = report.getQa();
        if (qa.size() < 2) {
            return;
        }
        List<Map<String, Object>> input = new ArrayList<>();
        for (int i = 0; i < qa.size(); i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("no", i + 1);
            m.put("kind", qa.get(i).isFromCandidate() ? "지원자 질문" : "면접관 질문");
            m.put("question", qa.get(i).getQuestion());
            m.put("answer", qa.get(i).getAnswer());
            input.add(m);
        }
        ReportSortResult result;
        try {
            result = aiClient.call(AiPurpose.REPORT_SORT, candidateId, promptLoader.load("report_sort"),
                    "<qa>\n" + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(input) + "\n</qa>",
                    ReportSortResult.class, r -> r.validate(qa.size()));
        } catch (RuntimeException e) {
            log.error("보고서 질문 정렬 실패 candidateId={}", candidateId, e);
            throw new BusinessException("질문을 정렬하지 못했습니다. " + e.getMessage());
        }
        report.setQa(reorder(qa, result.getOrder()));
        save(candidateId, report);
    }

    /** 새 순서(1부터)대로 놓고, 면접관 질문을 지원자 질문보다 앞에 둔다 */
    static List<QaItem> reorder(List<QaItem> qa, List<Integer> order) {
        List<QaItem> sorted = order.stream().map(n -> qa.get(n - 1)).toList();
        List<QaItem> result = new ArrayList<>(sorted.stream().filter(q -> !q.isFromCandidate()).toList());
        result.addAll(sorted.stream().filter(QaItem::isFromCandidate).toList());
        return result;
    }

    public static InterviewReport parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            InterviewReport r = JSON.readValue(json, InterviewReport.class);
            r.clean();
            return r;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private InterviewReport require(Long candidateId) {
        InterviewReport report = get(candidateId);
        if (report == null) {
            throw new BusinessException("보고서를 먼저 만드세요.");
        }
        return report;
    }

    /** 면접 질문지에서 답변을 적은 질문 (질문지 순서) */
    private List<InterviewQuestion> answeredSheet(Long candidateId) {
        return questionMapper.findByCandidateId(candidateId).stream()
                .filter(q -> q.isMerged() && q.getAnswer() != null && !q.getAnswer().isBlank()).toList();
    }

    static List<QaItem> qa(String source, InterviewRecord record, List<InterviewQuestion> sheet) {
        if (InterviewReport.SHEET.equals(source)) {
            return sheet.stream()
                    .map(q -> QaItem.of(QaItem.INTERVIEWER, q.getContent(), q.getAnswer(), q.getAnswerExcerpt()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (record == null) {
            return new ArrayList<>();
        }
        // 면접관 질문 먼저, 각각은 녹음본 순서
        return Stream.concat(record.getInterviewerItems().stream(), record.getCandidateItems().stream())
                .map(q -> QaItem.of(q.getKind(), q.getQuestion(), q.getAnswer(), q.getExcerpt()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** 파견 가능 여부·입사 가능 시기·희망연봉. 질문지 답변도 녹음본도 없으면 묻지 않는다 */
    private ReportInfoResult interviewInfo(Candidate c, InterviewRecord record, List<InterviewQuestion> sheet) {
        boolean hasTranscript = c.getTranscript() != null && !c.getTranscript().isBlank();
        boolean hasRecord = record != null && !record.getItems().isEmpty();
        if (sheet.isEmpty() && !hasTranscript && !hasRecord) {
            return null;
        }
        List<Map<String, String>> notes = sheet.stream()
                .map(q -> Map.of("question", q.getContent(), "answer", q.getAnswer())).toList();
        StringBuilder sb = new StringBuilder("<interviewer_notes>\n")
                .append(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(notes)).append("\n</interviewer_notes>\n\n");
        if (hasTranscript) {
            sb.append("<transcript>\n").append(c.getTranscript()).append("\n</transcript>");
        } else if (hasRecord) {
            List<Map<String, String>> items = record.getItems().stream()
                    .map(q -> Map.of("question", q.getQuestion(), "answer", q.getAnswer())).toList();
            sb.append("<transcript>\n").append(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(items)).append("\n</transcript>");
        }
        try {
            return aiClient.call(AiPurpose.REPORT_INFO, c.getId(), promptLoader.load("report_info"), sb.toString(),
                    ReportInfoResult.class, ReportInfoResult::validate);
        } catch (RuntimeException e) {
            // 못 찾으면 빈칸으로 두고 면접관이 적는다
            log.error("보고서 지원자 정보 찾기 실패 candidateId={}", c.getId(), e);
            return null;
        }
    }

    static List<InterviewReport.InfoRow> info(Candidate c, CandidateProfile profile, CareerEvalResult eval,
            ReportInfoResult interview, LocalDate today) {
        CareerEvalResult.Certificates certs = eval == null ? null : eval.getCertificates();
        List<InterviewReport.InfoRow> rows = new ArrayList<>();
        rows.add(InterviewReport.InfoRow.of("성명", c.getDisplayName()));
        rows.add(InterviewReport.InfoRow.of("나이", profile == null ? "" : age(profile.getBirth(), today)));
        rows.add(InterviewReport.InfoRow.of("경력", career(profile, eval)));
        rows.add(InterviewReport.InfoRow.of("학력", education(profile, eval)));
        rows.add(InterviewReport.InfoRow.of("정보처리기사", certs == null ? "" : has(certs.getInfoEngineer())));
        rows.add(InterviewReport.InfoRow.of("SQLD", certs == null ? "" : has(certs.getSqld())));
        rows.add(InterviewReport.InfoRow.of("파견 가능 여부", interview == null ? "" : interview.getDispatch()));
        rows.add(InterviewReport.InfoRow.of("입사 가능 시기", interview == null ? "" : interview.getStartDate()));
        rows.add(InterviewReport.InfoRow.of("희망연봉", interview == null ? "" : interview.getDesiredSalary()));
        return rows;
    }

    private static String has(Boolean b) {
        return Boolean.TRUE.equals(b) ? "있음" : "없음";
    }

    private static final Pattern FULL_YEAR = Pattern.compile("((?:19|20)\\d{2})(?:\\D{1,3}(\\d{1,2})(?:\\D{1,3}(\\d{1,2}))?)?");
    private static final Pattern AGE = Pattern.compile("(\\d{2,3})\\s*세");
    private static final Pattern SHORT_YEAR = Pattern.compile("(\\d{2})\\s*년생");

    /** 생년월일이나 나이 표기로 만 나이를 적는다. 읽지 못하면 원문 그대로 */
    static String age(String birth, LocalDate today) {
        if (birth == null || birth.isBlank()) {
            return "";
        }
        Matcher m = AGE.matcher(birth);
        if (m.find()) {
            return "만 " + Integer.parseInt(m.group(1)) + "세";
        }
        Integer year = null;
        int month = 0;
        int day = 0;
        m = FULL_YEAR.matcher(birth);
        if (m.find()) {
            year = Integer.parseInt(m.group(1));
            month = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            day = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        } else {
            m = SHORT_YEAR.matcher(birth);
            if (m.find()) {
                int yy = Integer.parseInt(m.group(1));
                year = yy <= today.getYear() % 100 ? 2000 + yy : 1900 + yy;
            }
        }
        if (year == null || year > today.getYear()) {
            return birth.strip();
        }
        int age = today.getYear() - year;
        if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
            if (today.getMonthValue() < month || (today.getMonthValue() == month && today.getDayOfMonth() < day)) {
                age--;
            }
            return "만 " + age + "세 (" + year + "년생)";
        }
        return "만 " + (age - 1) + "~" + age + "세 (" + year + "년생)";
    }

    static String career(CandidateProfile profile, CareerEvalResult eval) {
        if (eval != null && eval.getTotalMonths() != null) {
            int total = eval.getTotalMonths();
            if (total == 0) {
                return "신입";
            }
            String s = "총 " + CareerGauge.format(total);
            Integer rel = eval.getRelevantMonths();
            return rel != null && rel != total ? s + " (포지션 관련 " + CareerGauge.format(rel) + ")" : s;
        }
        return profile == null ? "" : profile.getTotalCareer();
    }

    static String education(CandidateProfile profile, CareerEvalResult eval) {
        if (eval != null && eval.getFinalEducation() != null && !eval.getFinalEducation().isBlank()) {
            return eval.getFinalEducation();
        }
        if (profile == null || profile.getEducation().isEmpty()) {
            return "";
        }
        CandidateProfile.Education e = profile.getEducation().get(0);
        return String.join(" ", Stream.of(e.getSchool(), e.getMajor(), e.getStatus())
                .filter(s -> s != null && !s.isBlank()).toList());
    }
}
