package com.example.interview.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.interview.ai.AiCallException;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.ChatMessage;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Position;
import com.example.interview.domain.CandidateFile;
import com.example.interview.domain.PositionFile;
import com.example.interview.domain.PositionImage;
import com.example.interview.domain.QuestionType;
import com.example.interview.resume.ResumeProcessingException;
import com.example.interview.service.BatchCheckService;
import com.example.interview.service.BusinessException;
import com.example.interview.service.CandidateService;
import com.example.interview.service.CareerEvalService;
import com.example.interview.service.CustomQuestionTypeService;
import com.example.interview.service.ExtractionService;
import com.example.interview.service.InterviewRecordService;
import com.example.interview.service.ChatService;
import com.example.interview.service.InterviewReportService;
import com.example.interview.service.PositionService;
import com.example.interview.service.PrivacyModeService;
import com.example.interview.service.RankingService;
import com.example.interview.service.ReportService;
import com.example.interview.web.dto.ConditionForm;
import com.example.interview.web.dto.PositionSummary;
import com.example.interview.web.dto.QuestionOrder;
import com.example.interview.web.dto.RankingRow;
import com.example.interview.web.dto.ResultReport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 평가 항목 추출(/positions) → 포지션 적합도 검사(/fit, 지원자는 /results/{id}) → 지원자 비교(/ranking) 탭.
 * 편집은 fetch(JSON)로 저장한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ExtractController {

    private static final int LIST_SIZE = 1000;
    private static final int SIDE_PAGE = 30;

    private final PositionService positionService;
    private final ExtractionService extractionService;
    private final ChatService chatService;
    private final ReportService reportService;
    private final RankingService rankingService;
    private final BatchCheckService batchCheckService;
    private final CandidateService candidateService;
    private final CustomQuestionTypeService customQuestionTypeService;
    private final CareerEvalService careerEvalService;
    private final PrivacyModeService privacyMode;

    /** 개인정보 엄격 보호 모드: 지원자 한 명씩, 가린 이력서를 확인한 뒤 검사 */
    @ModelAttribute("strictPrivacy")
    public boolean strictPrivacy() {
        return privacyMode.isStrict();
    }

    @GetMapping({"/", "/extract"})
    public String home() {
        return "redirect:/positions";
    }

    @GetMapping("/positions")
    public String newPosition(Model model) {
        addSide(model, positionService.recent(SIDE_PAGE));
        return "positions";
    }

    /** 꽉 찬 페이지로 끝나면 더 있을 수 있으니 이어 불러올 자리를 준다 */
    private void addSide(Model model, List<PositionSummary> side) {
        model.addAttribute("positions", side);
        model.addAttribute("sidePage", SIDE_PAGE);
        if (!side.isEmpty() && side.size() % SIDE_PAGE == 0) {
            model.addAttribute("sideNext", side.size());
        }
    }

    /** 왼쪽 포지션 목록 이어서 불러오기 (스크롤 끝) */
    @GetMapping("/positions/more")
    public String morePositions(@RequestParam(defaultValue = "0") int offset, Model model) {
        model.addAttribute("positions", positionService.recent(SIDE_PAGE, offset));
        return "positions-more";
    }

    @PostMapping("/positions")
    public String createPosition(@RequestParam(required = false) String jdText,
            @RequestParam(name = "files", required = false) List<MultipartFile> files, RedirectAttributes ra) {
        Long id;
        try {
            id = positionService.create(jdText, files);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("jdText", jdText);
            return "redirect:/positions";
        }
        extractCriteria(id, ra);
        return "redirect:/positions/" + id;
    }

    @GetMapping("/positions/{id}")
    public String position(@PathVariable Long id, Model model) {
        addSide(model, positionService.recentUntil(SIDE_PAGE, id));
        addPosition(model, id);
        return "positions";
    }

    @PostMapping("/positions/{id}/criteria/extract")
    public String reextract(@PathVariable Long id, RedirectAttributes ra) {
        positionService.get(id);
        extractCriteria(id, ra);
        return "redirect:/positions/" + id;
    }

    @PostMapping("/positions/{id}/delete")
    public String deletePosition(@PathVariable Long id, RedirectAttributes ra) {
        positionService.delete(id);
        ra.addFlashAttribute("message", "삭제했습니다.");
        return "redirect:/positions";
    }

    @GetMapping("/positions/{id}/images/{imageId}")
    public ResponseEntity<byte[]> image(@PathVariable Long id, @PathVariable Long imageId) {
        PositionImage image = positionService.image(id, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.getImageData());
    }

    @GetMapping("/positions/{id}/files/{fileId}")
    public ResponseEntity<byte[]> file(@PathVariable Long id, @PathVariable Long fileId) {
        PositionFile file = positionService.file(id, fileId);
        return download(file.getFileName(), file.getContentType(), file.getFileData());
    }

    /** 올린 이름 그대로 내려받게 한다 */
    private static ResponseEntity<byte[]> download(String name, String contentType, byte[] data) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(data);
    }

    @PatchMapping("/positions/{id}")
    @ResponseBody
    public Map<String, String> renamePosition(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Map.of("title", positionService.rename(id, body.get("title")));
    }

    @PatchMapping("/positions/{id}/conditions")
    @ResponseBody
    public ResponseEntity<Void> updateConditions(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String before = positionService.get(id).getRequiredExperience();
        positionService.updateConditions(id, body.get("requiredExperience"), body.get("requiredEducation"),
                body.get("requiredMajor"));
        // 경력 조건이 바뀌면 검사를 마친 지원자의 경력 판단을 뒤에서 다시 묻는다 (이력서는 보내지 않는다)
        if (!java.util.Objects.equals(before, positionService.get(id).getRequiredExperience())) {
            careerEvalService.rejudgeCareer(id);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/positions/{id}/criteria")
    @ResponseBody
    public EvalCriterion addCriterion(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return positionService.addCriterion(id, body.get("name"), body.get("description"));
    }

    @PatchMapping("/positions/{id}/criteria/{criterionId}")
    @ResponseBody
    public ResponseEntity<Void> updateCriterion(@PathVariable Long id, @PathVariable Long criterionId,
            @RequestBody Map<String, String> body) {
        positionService.updateCriterion(id, criterionId, body.get("name"), body.get("description"));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/positions/{id}/exclusions")
    @ResponseBody
    public ResponseEntity<Void> updateExclusions(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        positionService.updateExclusions(id, Boolean.TRUE.equals(body.get("career")), Boolean.TRUE.equals(body.get("education")));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/positions/{id}/criteria/{criterionId}")
    @ResponseBody
    public ResponseEntity<Void> deleteCriterion(@PathVariable Long id, @PathVariable Long criterionId) {
        positionService.deleteCriterion(id, criterionId);
        return ResponseEntity.noContent().build();
    }

    /** 포지션 적합도 검사: applicant-1, applicant-2 … 칸마다 한 명. 빈 칸은 건너뛴다. 가리기부터 검사까지 한 번에 돈다 */
    @PostMapping("/positions/{id}/batch")
    public String batch(@PathVariable Long id, MultipartHttpServletRequest request, RedirectAttributes ra) {
        List<List<MultipartFile>> applicants = new ArrayList<>();
        request.getMultiFileMap().entrySet().stream()
                .filter(e -> e.getKey().matches("applicant-\\d+"))
                .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getKey().substring("applicant-".length()))))
                .forEach(e -> applicants.add(e.getValue()));
        try {
            List<Long> ids = batchCheckService.start(id, applicants);
            if (ids.size() == 1) {
                return "redirect:/results/" + ids.get(0);
            }
            ra.addFlashAttribute("message", ids.size() + "명의 검사를 시작했습니다.");
        } catch (BusinessException | ResumeProcessingException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/fit?position=" + id;
    }

    /** 적합도 검사 화면의 지원자 목록 (다수 검사 진행 상황을 새로 그릴 때) */
    @GetMapping("/positions/{id}/candidates")
    @ResponseBody
    public List<Map<String, Object>> candidateList(@PathVariable Long id) {
        positionService.get(id);
        return rankingService.rank(id).stream()
                .sorted(Comparator.comparing(RankingRow::getCandidateId).reversed())
                .map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", row.getCandidateId());
                    m.put("name", row.getDisplayName());
                    m.put("at", row.getCreatedText());
                    m.put("state", row.getState());
                    m.put("status", row.getStatus().name());
                    m.put("inProgress", row.isInProgress());
                    m.put("label", row.getStatusLabel());
                    m.put("score", row.getScore() == null ? null : row.getScore().getTotalText());
                    m.put("max", row.getScore() == null ? null : row.getScore().getMax());
                    return m;
                }).toList();
    }

    @GetMapping("/results/{id}")
    public String result(@PathVariable Long id, Model model) {
        ResultReport r = reportService.report(id);
        // 검사가 끝났으면 질문을 추출하기 전에도 공통 질문은 질문지로 보인다
        if (r.isFitChecked() && r.getSections().isEmpty() && r.getExtracted().isEmpty()) {
            extractionService.startCommonSheet(id);
            r = reportService.report(id);
        }
        model.addAttribute("r", r);
        model.addAttribute("record", InterviewRecordService.parse(r.getCandidate().getRecordJson()));
        model.addAttribute("hasReport", r.getCandidate().getReportJson() != null);
        // 보고서 단계: 저장된 보고서를 그대로 (맞추기는 그 안의 버튼으로만)
        model.addAttribute("report", InterviewReportService.parse(r.getCandidate().getReportJson()));
        model.addAttribute("hasRecord", InterviewRecordService.parse(r.getCandidate().getRecordJson()) != null);
        model.addAttribute("candFiles", candidateService.files(id));
        model.addAttribute("customTypes", customQuestionTypeService.list());
        addFit(model, r.getPosition().getId());
        return "fit";
    }

    @GetMapping("/results/{id}/files/{fileId}")
    public ResponseEntity<byte[]> candidateFile(@PathVariable Long id, @PathVariable Long fileId) {
        CandidateFile file = candidateService.file(id, fileId);
        return download(file.getFileName(), file.getContentType(), file.getFileData());
    }

    /** 저장해 둔 원본 파일로 가리기부터 다시 */
    @PostMapping("/results/{id}/restart")
    public String restart(@PathVariable Long id, RedirectAttributes ra) {
        try {
            batchCheckService.restart(id);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/results/" + id;
    }

    @PostMapping("/results/{id}/check")
    public String checkFit(@PathVariable Long id, @RequestParam(required = false) String maskedText,
            RedirectAttributes ra) {
        try {
            extractionService.checkFit(id, maskedText);
            // 검사 직후 한 번만 도장 찍는 애니메이션
            ra.addFlashAttribute("justChecked", true);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/results/" + id;
    }

    @PostMapping("/results/{id}/questions/generate")
    public String extractQuestions(@PathVariable Long id,
            @RequestParam MultiValueMap<String, String> form, RedirectAttributes ra) {
        // List<String>로 받으면 값이 하나일 때 쉼표로 쪼개지므로 ("협업, 갈등") 폼 값 그대로 받는다
        var result = extractionService.extractQuestionsByNames(id, form.getOrDefault("type", List.of()));
        String message = "질문 " + result.count() + "개를 추출했습니다.";
        if (result.skipped().contains(QuestionType.EXPERIENCE)) {
            message += " 모든 평가 기준을 충족해 '해본 적 있어?' 질문은 만들지 않았습니다.";
        } else if (!result.skipped().isEmpty()) {
            message += " 충족·부분 충족한 평가 기준이 없어 이력서 내용에 대한 질문은 만들지 않았습니다.";
        }
        ra.addFlashAttribute("message", message);
        return "redirect:/results/" + id + "#extract";
    }

    /** AI와 대화: 지금 단계의 대화와 AI가 보는 자료 */
    @GetMapping("/results/{id}/chat")
    @ResponseBody
    public Map<String, Object> chat(@PathVariable Long id, @RequestParam(defaultValue = "BEFORE") String stage) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("messages", chatService.history(id).stream().map(ExtractController::chatJson).toList());
        m.put("sources", chatService.sources(id, stage));
        return m;
    }

    /**
     * {"stage": "BEFORE" | "AFTER", "message": "..."} → AI의 답을 스트림으로 (사용자 2026-10-28).
     * 한 줄에 JSON 하나(NDJSON): {"d": 조각}…, 끝에 {"done": true, "content": 전체} 또는 {"error": 메시지}.
     * 빈 질문 같은 검사 오류는 스트림을 열기 전에 일반 오류로 돌려준다.
     */
    @PostMapping("/results/{id}/chat")
    public ResponseEntity<StreamingResponseBody> sendChat(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ChatService.Prepared prepared = chatService.prepare(id, body.get("stage"), body.get("message"));
        StreamingResponseBody stream = out -> {
            java.util.function.Consumer<Map<String, Object>> write = m -> {
                try {
                    out.write((CHAT_JSON.writeValueAsString(m) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    out.flush();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            };
            try {
                ChatMessage reply = chatService.answer(prepared, d -> write.accept(Map.of("d", d)));
                write.accept(Map.of("done", true, "content", reply.getContent()));
            } catch (BusinessException e) {
                write.accept(Map.of("error", e.getMessage()));
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson;charset=UTF-8"))
                .header("X-Accel-Buffering", "no")
                .body(stream);
    }

    private static final tools.jackson.databind.json.JsonMapper CHAT_JSON = tools.jackson.databind.json.JsonMapper.builder().build();

    @DeleteMapping("/results/{id}/chat")
    public ResponseEntity<Void> resetChat(@PathVariable Long id) {
        chatService.reset(id);
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> chatJson(ChatMessage msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", msg.getRole());
        m.put("content", msg.getContent());
        return m;
    }

    @PostMapping("/results/{id}/questions/merge")
    public String mergeQuestions(@PathVariable Long id) {
        extractionService.mergeQuestions(id);
        return "redirect:/results/" + id + "#questions";
    }

    @PatchMapping("/results/{id}")
    @ResponseBody
    public Map<String, String> renameCandidate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Map.of("name", candidateService.rename(id, body.get("name")));
    }

    /** 면접 기록: {"interviewed": true, "date": "2026-10-22T14:30" 또는 "", "decision": "PASS" | "FAIL" | ""} */
    @PatchMapping("/results/{id}/interview")
    @ResponseBody
    public Map<String, Object> updateInterview(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object date = body.get("date");
        LocalDateTime day;
        try {
            String s = date == null ? "" : date.toString().strip();
            day = s.isEmpty() ? null : s.length() == 10 ? LocalDate.parse(s).atStartOfDay() : LocalDateTime.parse(s);
        } catch (DateTimeParseException e) {
            throw new BusinessException("날짜 형식이 아닙니다.");
        }
        Candidate c = candidateService.updateInterview(id, Boolean.TRUE.equals(body.get("interviewed")), day,
                body.get("decision") == null ? null : body.get("decision").toString());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("interviewed", c.isInterviewed());
        m.put("date", c.getInterviewDate() == null ? "" : c.getInterviewDate().toString());
        m.put("decision", c.getDecision() == null ? "" : c.getDecision());
        return m;
    }

    @PostMapping("/results/{id}/delete")
    public String deleteCandidate(@PathVariable Long id, RedirectAttributes ra) {
        Long positionId = reportService.report(id).getPosition().getId();
        extractionService.deleteCandidate(id);
        ra.addFlashAttribute("message", "삭제했습니다.");
        return "redirect:/fit?position=" + positionId;
    }

    @PostMapping("/results/{id}/questions")
    @ResponseBody
    public Map<String, Long> addQuestion(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Map.of("id", extractionService.addQuestion(id, body.get("section"), body.get("content")));
    }

    @PatchMapping("/results/{id}/questions/{questionId}")
    @ResponseBody
    public ResponseEntity<Void> updateQuestion(@PathVariable Long id, @PathVariable Long questionId,
            @RequestBody Map<String, String> body) {
        extractionService.updateQuestion(id, questionId, body.get("content"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/results/{id}/questions/{questionId}")
    @ResponseBody
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id, @PathVariable Long questionId) {
        extractionService.deleteQuestion(id, questionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/results/{id}/questions/order")
    @ResponseBody
    public ResponseEntity<Void> reorderQuestions(@PathVariable Long id, @RequestBody List<QuestionOrder> order) {
        extractionService.reorderQuestions(id, order);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/results/{id}/questions/{questionId}/answer")
    @ResponseBody
    public ResponseEntity<Void> saveAnswer(@PathVariable Long id, @PathVariable Long questionId,
            @RequestParam(required = false) String answer) {
        extractionService.saveAnswer(id, questionId, answer);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/results/{id}/questions/{questionId}/checked")
    @ResponseBody
    public ResponseEntity<Void> checkQuestion(@PathVariable Long id, @PathVariable Long questionId,
            @RequestParam boolean checked) {
        extractionService.checkQuestion(id, questionId, checked);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/results/{id}/questions/{questionId}/must-ask")
    @ResponseBody
    public ResponseEntity<Void> markMustAsk(@PathVariable Long id, @PathVariable Long questionId,
            @RequestParam boolean mustAsk) {
        extractionService.markMustAsk(id, questionId, mustAsk);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fit")
    public String fit(@RequestParam(required = false) Long position, Model model) {
        Long id = choose(model, position);
        if (id != null) {
            addFit(model, id);
        }
        return "fit";
    }

    @GetMapping("/ranking")
    public String ranking(@RequestParam(required = false) Long position, Model model) {
        Long id = choose(model, position);
        if (id != null) {
            model.addAttribute("position", positionService.get(id));
            model.addAttribute("criteria", positionService.criteria(id));
            List<RankingRow> rows = rankingService.rank(id);
            model.addAttribute("rows", rows);
            model.addAttribute("excludedCount", rows.stream().filter(r -> "EXCLUDED".equals(r.getState())).count());
            model.addAttribute("uncheckedCount", rows.stream().filter(r -> "UNCHECKED".equals(r.getState())).count());
            model.addAttribute("staleCount", rows.stream().filter(r -> "STALE".equals(r.getState())).count());
        }
        return "ranking";
    }

    /** 평가항목이 있는 포지션 목록을 넣고, 고른 포지션(없으면 가장 최근 것)의 id를 돌려준다 */
    private Long choose(Model model, Long requested) {
        List<PositionSummary> positions = positionService.recent(LIST_SIZE).stream()
                .filter(p -> p.getCriteriaCount() > 0).toList();
        model.addAttribute("positions", positions);
        if (requested != null) {
            positionService.get(requested);
            return requested;
        }
        return positions.isEmpty() ? null : positions.get(0).getId();
    }

    /** 적합도 검사 화면: 포지션, 평가항목, 지원자 목록(최근 순, 점수 포함) */
    private void addFit(Model model, Long positionId) {
        if (!model.containsAttribute("positions")) {
            model.addAttribute("positions", positionService.recent(LIST_SIZE).stream()
                    .filter(p -> p.getCriteriaCount() > 0 || p.getId().equals(positionId)).toList());
        }
        model.addAttribute("position", positionService.get(positionId));
        model.addAttribute("criteria", positionService.criteria(positionId));
        model.addAttribute("candidates", rankingService.rank(positionId).stream()
                .sorted(Comparator.comparing(RankingRow::getCandidateId).reversed()).toList());
    }

    private void addPosition(Model model, Long positionId) {
        Position position = positionService.get(positionId);
        model.addAttribute("position", position);
        model.addAttribute("conds", ConditionForm.of(position));
        model.addAttribute("criteria", positionService.criteria(positionId));
        model.addAttribute("files", positionService.files(positionId));
    }

    private void extractCriteria(Long positionId, RedirectAttributes ra) {
        try {
            positionService.extractCriteria(positionId);
            ra.addFlashAttribute("justExtracted", true); // 공고와 평가 기준을 글 쓰듯 한 번 보여 준다
        } catch (AiCallException e) {
            log.warn("평가항목 추출 실패 positionId={}: {}", positionId, e.getMessage());
            ra.addFlashAttribute("error", "평가 기준을 추출하지 못했습니다. " + e.getMessage());
        }
    }
}
