package com.example.interview.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.interview.ai.AiClient;
import com.example.interview.ai.PromptLoader;
import com.example.interview.ai.dto.InterviewRecordResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.InterviewRecord;
import com.example.interview.domain.QaItem;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.resume.DocumentTextExtractor;
import com.example.interview.resume.PdfTextExtractor;
import com.example.interview.resume.QuoteVerifier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 면접 기록 (사용자 요청): 면접 녹음본(클로바 노트 등으로 옮긴 글)만 보고 질문-답변 목록을 만든다.
 * 질문지와 상관없이 녹음본에 나온 질문은 모두, 녹음본 순서대로 넣고, 면접관 질문과 지원자가 회사에 한 질문을 나눈다.
 * 녹음본은 가리지 않고 그대로 보내고 저장한다 (사용자 결정, 이력서 규칙의 예외). 질문지 답변은 건드리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewRecordService {

    static final int MAX_CHARS = 200_000;
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CandidateService candidateService;
    private final CandidateMapper candidateMapper;
    private final DocumentTextExtractor documentTextExtractor;
    private final QuoteVerifier quoteVerifier;
    private final AiClient aiClient;
    private final PromptLoader promptLoader;

    public InterviewRecord create(Long candidateId, String pasted, MultipartFile file) {
        candidateService.get(candidateId);
        String transcript = transcript(pasted, file);
        InterviewRecordResult result;
        try {
            result = aiClient.call(AiPurpose.INTERVIEW_RECORD, candidateId, promptLoader.load("interview_record"),
                    "<transcript>\n" + transcript + "\n</transcript>", InterviewRecordResult.class, InterviewRecordResult::validate);
        } catch (RuntimeException e) {
            log.error("면접 기록 만들기 실패 candidateId={}", candidateId, e);
            throw new BusinessException("녹음본으로 면접 내용을 정리하지 못했습니다. " + e.getMessage());
        }
        InterviewRecord record = new InterviewRecord();
        record.setCreatedAt(LocalDateTime.now().format(CREATED_AT));
        record.setItems(result.getItems().stream()
                .map(i -> QaItem.of(i.getKind(), i.getQuestion(), i.getAnswer(), verifiedExcerpt(i.getExcerpt(), transcript)))
                .toList());
        candidateMapper.updateRecord(candidateId, transcript, JSON.writeValueAsString(record));
        return record;
    }

    public static InterviewRecord parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, InterviewRecord.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** " … "로 이은 조각이 모두 녹음본에 있으면 그대로, 아니면 버린다 (지어낸 인용을 남기지 않는다) */
    String verifiedExcerpt(String excerpt, String transcript) {
        if (excerpt == null || excerpt.isBlank()) {
            return "";
        }
        for (String part : excerpt.split("…|\\.\\.\\.")) {
            if (!part.isBlank() && !quoteVerifier.verify(part, transcript)) {
                return "";
            }
        }
        return excerpt.strip();
    }

    private String transcript(String pasted, MultipartFile file) {
        StringBuilder sb = new StringBuilder();
        if (pasted != null && !pasted.isBlank()) {
            sb.append(pasted.strip());
        }
        if (file != null && !file.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(readFile(file));
        }
        String text = PdfTextExtractor.normalize(sb.toString());
        if (text.isBlank()) {
            throw new BusinessException("녹음본 글을 붙여넣거나 파일을 올리세요.");
        }
        if (text.length() > MAX_CHARS) {
            throw new BusinessException("녹음본이 너무 깁니다. " + MAX_CHARS + "자까지 넣을 수 있습니다.");
        }
        return text;
    }

    private String readFile(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        try {
            byte[] bytes = file.getBytes();
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".txt") || lower.endsWith(".md")) {
                String s = new String(bytes, StandardCharsets.UTF_8);
                return s.startsWith("﻿") ? s.substring(1) : s;
            }
            return documentTextExtractor.extract(name, bytes).text();
        } catch (IOException e) {
            throw new BusinessException(name + ": 파일을 읽을 수 없습니다.");
        } catch (RuntimeException e) {
            throw new BusinessException(e.getMessage());
        }
    }
}
