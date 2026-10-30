package com.example.interview.service;

import java.time.LocalDate;
import java.util.ArrayList;
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

    /**
     * 만들거나 지금 기록에 다시 맞춘다 (보고서 버튼을 누를 때마다, 사용자 2026-10-27).
     * 날짜·지원자 정보·질문과 답변은 새로 채우고, 보고서에만 있는 면접관 의견과 지원 포지션은 남긴다.
     * 질문과 답변은 두 출처(녹음본·질문지)를 모두 새로 복사해 두고(전환은 사본끼리), 고른 출처를 보인다.
     * 그 출처가 비었거나 처음이면 녹음본이 있으면 녹음본, 없으면 질문지.
     * 파견 가능 여부·입사 가능 시기·희망연봉은 면접 내용이 바뀌었을 때만 GPT에 다시 묻는다.
     */
    public InterviewReport create(Long candidateId) {
        Candidate c = candidateService.get(candidateId);
        InterviewReport old = parse(c.getReportJson());
        InterviewRecord record = InterviewRecordService.parse(c.getRecordJson());
        List<InterviewQuestion> sheet = answeredSheet(candidateId);

        InterviewReport report = new InterviewReport();
        report.setReportDate(LocalDate.now().toString());
        report.setInterviewDate(c.getInterviewDate() == null ? "" : c.getInterviewDate().toString());
        boolean fromRecord = record != null && !record.getItems().isEmpty();
        String source = fromRecord || sheet.isEmpty() ? InterviewReport.RECORD : InterviewReport.SHEET;
        if (old != null && !qa(old.getQaSource(), record, sheet).isEmpty()) {
            source = old.getQaSource();
        }
        report.setQaSource(source);
        report.setQa(qa(source, record, sheet));
        report.setQaOther(qa(InterviewReport.SHEET.equals(source) ? InterviewReport.RECORD : InterviewReport.SHEET, record, sheet));

        String input = infoInput(c, record, sheet);
        // 앞의 "v2": 찾는 항목이 바뀌면(직전연봉 추가, 2026-10-28) 예전 답을 쓰지 않고 한 번 다시 묻는다
        String key = input == null ? null : sha256("v2|" + input);
        InterviewReport.Found found;
        if (key == null) {
            found = null;
        } else if (old != null && key.equals(old.getInfoKey()) && old.getFound() != null) {
            found = old.getFound();
        } else {
            found = found(askInfo(c.getId(), input));
            if (found == null && old != null) {
                found = old.getFound(); // 실패하면 전에 찾은 값을 둔다
                key = old.getInfoKey();
            }
        }
        report.setInfoKey(found == null ? null : key);
        report.setFound(found);
        report.setInfo(info(c, ReportService.parseProfile(c.getProfileJson()), CareerEvalService.parse(c.getCareerEvalJson()),
                found, LocalDate.now()));
        report.setCerts(certs(CareerEvalService.parse(c.getCareerEvalJson())));

        if (old != null) {
            report.setPosition(old.getPosition());
            report.setOpinions(old.getOpinions());
        }
        if (report.getOpinions().isEmpty()) {
            report.getOpinions().add(new InterviewReport.Opinion());
        }
        save(candidateId, report);
        return report;
    }

    public void save(Long candidateId, InterviewReport report) {
        candidateService.get(candidateId);
        report.clean();
        candidateMapper.updateReport(candidateId, JSON.writeValueAsString(report));
    }

    /** 보고서 화면에서 고친 것을 저장한다. 화면이 모르는 찾은 값은 그대로 둔다 */
    public void saveEdited(Long candidateId, InterviewReport report) {
        InterviewReport old = require(candidateId);
        report.setInfoKey(old.getInfoKey());
        report.setFound(old.getFound());
        report.setQaOther(old.getQaOther());
        report.setQaSource(old.getQaSource());
        save(candidateId, report);
    }

    /**
     * 질문과 답변을 보고서에 저장해 둔 다른 쪽 사본(타이핑·녹음본)으로 바꾼다. 기록을 다시 가져오지 않고,
     * 지금 보이던 쪽은 고친 그대로 사본으로 남는다 (사용자 2026-10-28: 최신 기록 반영은 동기화로만).
     * 사본이 없는 예전 보고서만 처음 한 번 지금 기록으로 채운다.
     */
    public void useSource(Long candidateId, String source) {
        InterviewReport report = require(candidateId);
        String s = InterviewReport.SHEET.equals(source) ? InterviewReport.SHEET : InterviewReport.RECORD;
        if (s.equals(report.getQaSource())) {
            return;
        }
        List<QaItem> other = report.getQaOther();
        boolean snapshot = other != null;
        if (!snapshot) {
            Candidate c = candidateService.get(candidateId);
            other = qa(s, InterviewRecordService.parse(c.getRecordJson()), answeredSheet(candidateId));
        }
        if (other.isEmpty()) {
            String what = InterviewReport.SHEET.equals(s) ? "면접 질문지에 적은 답변이" : "면접 기록이";
            throw new BusinessException(snapshot ? "이 보고서에는 " + what + " 없습니다. 「최신 기록으로 동기화」로 가져오세요."
                    : what + " 없습니다.");
        }
        report.setQaOther(report.getQa());
        report.setQa(other);
        report.setQaSource(s);
        save(candidateId, report);
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

    private static InterviewReport.Found found(ReportInfoResult r) {
        if (r == null) {
            return null;
        }
        InterviewReport.Found f = new InterviewReport.Found();
        f.setDispatch(r.getDispatch());
        f.setStartDate(r.getStartDate());
        f.setLastSalary(r.getLastSalary());
        f.setDesiredSalary(r.getDesiredSalary());
        return f;
    }

    private static String sha256(String s) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** REPORT_INFO에 보낼 내용. 질문지 답변도 녹음본도 없으면 null */
    private static String infoInput(Candidate c, InterviewRecord record, List<InterviewQuestion> sheet) {
        boolean hasTranscript = c.getTranscript() != null && !c.getTranscript().isBlank();
        boolean hasRecord = record != null && !record.getItems().isEmpty();
        if (sheet.isEmpty() && !hasTranscript && !hasRecord) {
            return null;
        }
        List<Map<String, String>> notes = sheet.stream()
                .map(q -> qaMap(q.getContent(), q.getAnswer())).toList();
        StringBuilder sb = new StringBuilder("<interviewer_notes>\n")
                .append(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(notes)).append("\n</interviewer_notes>\n\n");
        if (hasTranscript) {
            sb.append("<transcript>\n").append(c.getTranscript()).append("\n</transcript>");
        } else if (hasRecord) {
            List<Map<String, String>> items = record.getItems().stream()
                    .map(q -> qaMap(q.getQuestion(), q.getAnswer())).toList();
            sb.append("<transcript>\n").append(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(items)).append("\n</transcript>");
        }
        return sb.toString();
    }

    /** 순서가 정해진 질문-답변 (Map.of는 실행마다 순서가 달라 해시가 바뀐다) */
    private static Map<String, String> qaMap(String question, String answer) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("question", question);
        m.put("answer", answer);
        return m;
    }

    private ReportInfoResult askInfo(Long candidateId, String input) {
        try {
            return aiClient.call(AiPurpose.REPORT_INFO, candidateId, promptLoader.load("report_info"), input,
                    ReportInfoResult.class, ReportInfoResult::validate);
        } catch (RuntimeException e) {
            // 못 찾으면 빈칸으로 두고 면접관이 적는다
            log.error("보고서 지원자 정보 찾기 실패 candidateId={}", candidateId, e);
            return null;
        }
    }

    static List<InterviewReport.InfoRow> info(Candidate c, CandidateProfile profile, CareerEvalResult eval,
            InterviewReport.Found interview, LocalDate today) {
        List<InterviewReport.InfoRow> rows = new ArrayList<>();
        rows.add(InterviewReport.InfoRow.of("성명", c.getDisplayName()));
        rows.add(InterviewReport.InfoRow.of("나이", profile == null ? "" : age(profile.getBirth(), today)));
        rows.add(InterviewReport.InfoRow.of("경력", career(profile, eval)));
        rows.add(InterviewReport.InfoRow.of("학력", education(profile, eval)));
        rows.add(InterviewReport.InfoRow.of("파견 가능 여부", interview == null ? "" : interview.getDispatch()));
        rows.add(InterviewReport.InfoRow.of("입사 가능 시기", interview == null ? "" : interview.getStartDate()));
        rows.add(InterviewReport.InfoRow.of("직전연봉/희망연봉", interview == null ? "" : salaries(interview.getLastSalary(), interview.getDesiredSalary())));
        return rows;
    }

    /** 한 칸에 "직전 / 희망". 하나만 찾았으면 다른 쪽은 "-" */
    private static String salaries(String last, String desired) {
        last = last == null ? "" : last.strip();
        desired = desired == null ? "" : desired.strip();
        if (last.isBlank() && desired.isBlank()) {
            return "";
        }
        return (last.isBlank() ? "-" : last) + " / " + (desired.isBlank() ? "-" : desired);
    }

    static List<InterviewReport.InfoRow> certs(CareerEvalResult eval) {
        CareerEvalResult.Certificates certs = eval == null ? null : eval.getCertificates();
        List<InterviewReport.InfoRow> rows = new ArrayList<>();
        rows.add(InterviewReport.InfoRow.of("정보처리기사", certs == null ? "" : has(certs.getInfoEngineer())));
        rows.add(InterviewReport.InfoRow.of("SQLD", certs == null ? "" : has(certs.getSqld())));
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
