package com.example.interview.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.example.interview.ai.AiClient;
import com.example.interview.ai.PromptLoader;
import com.example.interview.ai.dto.AutoAnswerResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.InterviewQuestion;
import com.example.interview.mapper.InterviewQuestionMapper;
import com.example.interview.resume.DocumentTextExtractor;
import com.example.interview.resume.PdfTextExtractor;
import com.example.interview.resume.QuoteVerifier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 자동 답변 채우기 (사용자 요청): 면접 녹취록(클로바 노트 등으로 옮긴 글)과 질문지를 보내 질문마다 지원자 답을 받는다.
 * 녹취록은 가리지 않고 그대로 보낸다 (사용자 결정, 이력서 규칙의 예외). 답변이 비어 있는 질문만 채운다.
 * 녹취 원문 부분(excerpt)은 녹취록에 실제로 있을 때만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoAnswerService {

    static final int MAX_CHARS = 200_000;
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final InterviewQuestionMapper questionMapper;
    private final CandidateService candidateService;
    private final DocumentTextExtractor documentTextExtractor;
    private final QuoteVerifier quoteVerifier;
    private final AiClient aiClient;
    private final PromptLoader promptLoader;
    private final TransactionTemplate transactionTemplate;

    /** @param filled 채운 질문 수, kept 이미 답변이 있어 그대로 둔 질문 수 */
    public record Filled(int filled, int kept) {
    }

    public Filled fill(Long candidateId, String pasted, MultipartFile file) {
        candidateService.get(candidateId);
        String transcript = transcript(pasted, file);
        List<InterviewQuestion> sheet = questionMapper.findByCandidateId(candidateId).stream()
                .filter(InterviewQuestion::isMerged).toList();
        if (sheet.isEmpty()) {
            throw new BusinessException("면접 질문지를 먼저 만드세요.");
        }

        AutoAnswerResult result;
        try {
            result = aiClient.call(AiPurpose.AUTO_ANSWER, candidateId, promptLoader.load("auto_answer"),
                    input(sheet, transcript), AutoAnswerResult.class, r -> r.validate(sheet.size()));
        } catch (RuntimeException e) {
            log.error("자동 답변 채우기 실패 candidateId={}", candidateId, e);
            throw new BusinessException("답변을 채우지 못했습니다. " + e.getMessage());
        }

        List<Integer> changed = new ArrayList<>();
        transactionTemplate.executeWithoutResult(status -> {
            for (AutoAnswerResult.Item a : result.getAnswers()) {
                InterviewQuestion q = sheet.get(a.getQuestionId().intValue() - 1);
                changed.add(questionMapper.fillAnswer(candidateId, q.getId(), a.getAnswer().strip(),
                        verifiedExcerpt(a.getExcerpt(), transcript)));
            }
        });
        int filled = (int) changed.stream().filter(n -> n > 0).count();
        return new Filled(filled, changed.size() - filled);
    }

    /** 질문지 번호(1부터)와 질문. 섹션은 흐름을 알도록 같이 보낸다 */
    static String input(List<InterviewQuestion> sheet, String transcript) {
        List<Object> questions = new ArrayList<>();
        for (int i = 0; i < sheet.size(); i++) {
            InterviewQuestion q = sheet.get(i);
            questions.add(new QuestionInput((long) (i + 1), q.getSection(), q.getContent()));
        }
        return "<questions>\n" + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(questions)
                + "\n</questions>\n\n<transcript>\n" + transcript + "\n</transcript>";
    }

    /** " … "로 이은 조각이 모두 녹취록에 있으면 그대로, 아니면 버린다 (지어낸 인용을 남기지 않는다) */
    String verifiedExcerpt(String excerpt, String transcript) {
        if (excerpt == null || excerpt.isBlank()) {
            return null;
        }
        for (String part : excerpt.split("…|\\.\\.\\.")) {
            if (!part.isBlank() && !quoteVerifier.verify(part, transcript)) {
                return null;
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
            throw new BusinessException("녹취록을 붙여넣거나 파일을 올리세요.");
        }
        if (text.length() > MAX_CHARS) {
            throw new BusinessException("녹취록이 너무 깁니다. " + MAX_CHARS + "자까지 넣을 수 있습니다.");
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

    record QuestionInput(Long question_id, String section, String content) {
    }
}
