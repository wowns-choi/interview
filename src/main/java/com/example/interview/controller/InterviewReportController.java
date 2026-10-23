package com.example.interview.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.interview.domain.Candidate;
import com.example.interview.domain.InterviewRecord;
import com.example.interview.domain.InterviewReport;
import com.example.interview.service.CandidateService;
import com.example.interview.service.InterviewRecordService;
import com.example.interview.service.InterviewReportService;
import com.example.interview.service.PositionService;

import lombok.RequiredArgsConstructor;

/** 면접 기록(녹음본으로 만든 질문-답변)과 합격자 보고서 */
@Controller
@RequiredArgsConstructor
public class InterviewReportController {

    private final CandidateService candidateService;
    private final PositionService positionService;
    private final InterviewRecordService recordService;
    private final InterviewReportService reportService;

    @PostMapping("/results/{id}/record")
    public String createRecord(@PathVariable Long id, @RequestParam(required = false) String transcript,
            @RequestParam(required = false) MultipartFile file, RedirectAttributes ra) {
        InterviewRecord record = recordService.create(id, transcript, file);
        ra.addFlashAttribute("message", "질문-답변 " + record.getItems().size() + "개를 정리했습니다.");
        return "redirect:/results/" + id + "#record";
    }

    @GetMapping("/results/{id}/report")
    public String report(@PathVariable Long id, Model model) {
        Candidate candidate = candidateService.get(id);
        InterviewReport report = InterviewReportService.parse(candidate.getReportJson());
        if (report == null) {
            return "redirect:/results/" + id;
        }
        model.addAttribute("candidate", candidate);
        model.addAttribute("position", positionService.get(candidate.getPositionId()));
        model.addAttribute("report", report);
        model.addAttribute("hasRecord", InterviewRecordService.parse(candidate.getRecordJson()) != null);
        return "report";
    }

    @PostMapping("/results/{id}/report")
    public String createReport(@PathVariable Long id) {
        reportService.create(id);
        return "redirect:/results/" + id + "/report";
    }

    @PutMapping("/results/{id}/report")
    public ResponseEntity<Void> saveReport(@PathVariable Long id, @RequestBody InterviewReport report) {
        reportService.save(id, report);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/results/{id}/report/source")
    public String useSource(@PathVariable Long id, @RequestParam String source) {
        reportService.useSource(id, source);
        return "redirect:/results/" + id + "/report";
    }

    @PostMapping("/results/{id}/report/info")
    public String refreshInfo(@PathVariable Long id, RedirectAttributes ra) {
        int filled = reportService.refreshInfo(id);
        ra.addFlashAttribute("message", filled == 0 ? "면접 질문-답변에서 파견 가능 여부·입사 가능 시기·희망연봉을 찾지 못했습니다."
                : "지원자 정보 " + filled + "칸을 채웠습니다.");
        return "redirect:/results/" + id + "/report";
    }

    @PostMapping("/results/{id}/report/sort")
    public String sort(@PathVariable Long id, RedirectAttributes ra) {
        reportService.sort(id);
        ra.addFlashAttribute("message", "보고서를 읽는 사람이 이해하기 쉬운 순서로 정렬했습니다.");
        return "redirect:/results/" + id + "/report";
    }
}
