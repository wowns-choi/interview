package com.example.interview.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.interview.domain.InterviewRecord;
import com.example.interview.domain.InterviewReport;
import com.example.interview.domain.QaItem;
import com.example.interview.service.BusinessException;
import com.example.interview.service.CandidateService;
import com.example.interview.service.InterviewRecordService;
import com.example.interview.service.InterviewReportService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** 면접 기록(녹음본으로 만든 질문-답변)과 합격자 보고서 */
@Controller
@RequiredArgsConstructor
public class InterviewReportController {

    private final CandidateService candidateService;
    private final InterviewRecordService recordService;
    private final InterviewReportService reportService;

    @PostMapping("/results/{id}/record")
    public String createRecord(@PathVariable Long id, @RequestParam(required = false) String transcript,
            @RequestParam(required = false) MultipartFile file, RedirectAttributes ra) {
        InterviewRecord record = recordService.create(id, transcript, file);
        ra.addFlashAttribute("message", "질문-답변 " + record.getItems().size() + "개를 정리했습니다.");
        return "redirect:/results/" + id + "#after";
    }

    /**
     * 면접 내용 정리를 스트림으로 (사용자 2026-11-06). 한 줄에 JSON 하나(NDJSON): 항목마다 {"item": {...}},
     * 끝에 {"done": true, "count": n} 또는 {"error": 메시지}. 녹음본이 없는 것 같은 오류는 스트림을 열기 전에 일반 오류로.
     */
    @PostMapping("/results/{id}/record/stream")
    public ResponseEntity<StreamingResponseBody> streamRecord(@PathVariable Long id,
            @RequestParam(required = false) String transcript, @RequestParam(required = false) MultipartFile file,
            HttpServletResponse response) {
        InterviewRecordService.Prepared prepared = recordService.prepare(id, transcript, file);
        return NdjsonStream.of(response, write -> {
            try {
                InterviewRecord record = recordService.createStreaming(prepared, item -> write.accept(Map.of("item", itemJson(item))));
                write.accept(Map.of("done", true, "count", record.getItems().size()));
            } catch (BusinessException e) {
                write.accept(Map.of("error", e.getMessage()));
            }
        });
    }

    private static Map<String, Object> itemJson(QaItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", item.getKind());
        m.put("question", item.getQuestion());
        m.put("answer", item.getAnswer() == null ? "" : item.getAnswer());
        m.put("excerpt", item.getExcerpt() == null ? "" : item.getExcerpt());
        return m;
    }

    /** 보고서는 지원자 화면의 보고서 단계에 있다 (사용자 2026-10-28). 예전 주소는 그리로 보낸다 */
    @GetMapping("/results/{id}/report")
    public String report(@PathVariable Long id) {
        candidateService.get(id);
        return "redirect:/results/" + id + "#report";
    }

    @PostMapping("/results/{id}/report")
    public String createReport(@PathVariable Long id) {
        reportService.create(id);
        return "redirect:/results/" + id + "#report";
    }

    @PutMapping("/results/{id}/report")
    public ResponseEntity<Void> saveReport(@PathVariable Long id, @RequestBody InterviewReport report) {
        reportService.saveEdited(id, report);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/results/{id}/report/source")
    public String useSource(@PathVariable Long id, @RequestParam String source) {
        reportService.useSource(id, source);
        return "redirect:/results/" + id + "#report";
    }
}
