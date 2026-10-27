package com.example.interview.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.interview.domain.CustomQuestionType;
import com.example.interview.service.CustomQuestionTypeService;

import lombok.RequiredArgsConstructor;

/** 면접 질문 추출 창의 직접 만든 유형: 추가하면 바로 저장, 지우면 바로 삭제 */
@RestController
@RequestMapping("/question-types")
@RequiredArgsConstructor
public class CustomQuestionTypeController {

    private final CustomQuestionTypeService service;

    @PostMapping
    public CustomQuestionType add(@RequestBody Map<String, String> body) {
        return service.add(body.get("name"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
