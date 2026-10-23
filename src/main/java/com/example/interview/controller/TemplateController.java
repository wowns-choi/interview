package com.example.interview.controller;

import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.interview.domain.QuestionSection;
import com.example.interview.domain.QuestionTemplate;
import com.example.interview.service.TemplateService;
import com.example.interview.service.TemplateService.SectionOrder;

import lombok.RequiredArgsConstructor;

/**
 * "공통 질문 설정" 탭. 화면은 한 번 그리고, 이후 편집은 fetch(JSON)로 바로 저장한다.
 * 요청에는 X-Requested-With: fetch 헤더를 붙인다 (오류를 화면 이동 대신 본문으로 받기 위해).
 */
@Controller
@RequestMapping("/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("board", templateService.board());
        return "templates";
    }

    @PostMapping("/sections")
    @ResponseBody
    public QuestionSection createSection(@RequestBody Map<String, String> body) {
        return templateService.createSection(body.get("name"));
    }

    @PatchMapping("/sections/{id}")
    @ResponseBody
    public ResponseEntity<Void> renameSection(@PathVariable Long id, @RequestBody Map<String, String> body) {
        templateService.renameSection(id, body.get("name"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sections/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteSection(@PathVariable Long id) {
        templateService.deleteSection(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sections/{id}/questions")
    @ResponseBody
    public QuestionTemplate createQuestion(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return templateService.createQuestion(id, body.get("content"));
    }

    @PatchMapping("/questions/{id}")
    @ResponseBody
    public ResponseEntity<Void> updateQuestion(@PathVariable Long id, @RequestBody Map<String, String> body) {
        templateService.updateQuestion(id, body.get("content"));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/questions/{id}/must-ask")
    @ResponseBody
    public ResponseEntity<Void> setMustAsk(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        templateService.setMustAsk(id, Boolean.TRUE.equals(body.get("mustAsk")));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/questions/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        templateService.deleteQuestion(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    @ResponseBody
    public ResponseEntity<Void> reorder(@RequestBody List<SectionOrder> order) {
        templateService.reorder(order);
        return ResponseEntity.noContent().build();
    }
}
