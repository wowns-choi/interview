package com.example.interview.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.interview.ai.AiCallException;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.PositionImage;
import com.example.interview.resume.ResumeProcessingException;
import com.example.interview.service.BusinessException;
import com.example.interview.service.ExtractionService;
import com.example.interview.service.PositionService;
import com.example.interview.service.ReportService;
import com.example.interview.web.dto.QuestionOrder;
import com.example.interview.web.dto.ResultReport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "면접 질문 추출하기" 탭: 평가항목 추출 → 포지션 적합도 검사 → 면접 질문 추출을 한 화면에서 이어 간다.
 * 포지션 화면은 /positions/{id}, 지원자(이력서) 화면은 /results/{id}. 편집은 fetch(JSON)로 저장한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ExtractController {

    private static final int RECENT_SIZE = 20;

    private final PositionService positionService;
    private final ExtractionService extractionService;
    private final ReportService reportService;

    @GetMapping("/")
    public String home() {
        return "redirect:/extract";
    }

    @GetMapping("/extract")
    public String start(Model model) {
        model.addAttribute("recent", positionService.recent(RECENT_SIZE));
        return "extract";
    }

    @PostMapping("/extract")
    public String createPosition(@RequestParam(required = false) String jdText,
            @RequestParam(name = "files", required = false) List<MultipartFile> files, RedirectAttributes ra) {
        Long id;
        try {
            id = positionService.create(jdText, files);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("jdText", jdText);
            return "redirect:/extract";
        }
        extractCriteria(id, ra);
        return "redirect:/positions/" + id;
    }

    @GetMapping("/positions/{id}")
    public String position(@PathVariable Long id, Model model) {
        addPosition(model, id);
        return "extract";
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
        return "redirect:/extract";
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

    @PostMapping("/positions/{id}/criteria")
    @ResponseBody
    public EvalCriterion addCriterion(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return positionService.addCriterion(id, body.get("name"), body.get("description"), body.get("requirement"));
    }

    @PatchMapping("/positions/{id}/criteria/{criterionId}")
    @ResponseBody
    public ResponseEntity<Void> updateCriterion(@PathVariable Long id, @PathVariable Long criterionId,
            @RequestBody Map<String, String> body) {
        positionService.updateCriterion(id, criterionId, body.get("name"), body.get("description"),
                body.get("requirement"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/positions/{id}/criteria/{criterionId}")
    @ResponseBody
    public ResponseEntity<Void> deleteCriterion(@PathVariable Long id, @PathVariable Long criterionId) {
        positionService.deleteCriterion(id, criterionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/positions/{id}/resume")
    public String uploadResume(@PathVariable Long id, @RequestParam(required = false) MultipartFile resume,
            RedirectAttributes ra) {
        try {
            return "redirect:/results/" + extractionService.uploadResume(id, resume) + "#fit";
        } catch (BusinessException | ResumeProcessingException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/positions/" + id + "#fit";
        }
    }

    @GetMapping("/results/{id}")
    public String result(@PathVariable Long id, Model model) {
        ResultReport r = reportService.report(id);
        model.addAttribute("r", r);
        addPosition(model, r.getPosition().getId());
        return "extract";
    }

    @PostMapping("/results/{id}/check")
    public String checkFit(@PathVariable Long id) {
        extractionService.checkFit(id);
        return "redirect:/results/" + id + "#fit";
    }

    @PostMapping("/results/{id}/questions/generate")
    public String generateQuestions(@PathVariable Long id) {
        extractionService.generateQuestions(id);
        return "redirect:/results/" + id + "#questions";
    }

    @PostMapping("/results/{id}/delete")
    public String deleteCandidate(@PathVariable Long id, RedirectAttributes ra) {
        Long positionId = reportService.report(id).getPosition().getId();
        extractionService.deleteCandidate(id);
        ra.addFlashAttribute("message", "삭제했습니다.");
        return "redirect:/positions/" + positionId + "#fit";
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

    private void addPosition(Model model, Long positionId) {
        model.addAttribute("position", positionService.get(positionId));
        model.addAttribute("criteria", positionService.criteria(positionId));
        model.addAttribute("imageIds", positionService.imageIds(positionId));
        model.addAttribute("candidates", extractionService.candidates(positionId));
    }

    private void extractCriteria(Long positionId, RedirectAttributes ra) {
        try {
            positionService.extractCriteria(positionId);
        } catch (AiCallException e) {
            log.warn("평가항목 추출 실패 positionId={}: {}", positionId, e.getMessage());
            ra.addFlashAttribute("error", "평가항목을 추출하지 못했습니다. " + e.getMessage());
        }
    }
}
