package com.example.interview.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.example.interview.ai.AiClient;
import com.example.interview.ai.AiImage;
import com.example.interview.ai.PromptLoader;
import com.example.interview.ai.dto.CriteriaResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.CriterionCategory;
import com.example.interview.domain.CriterionSource;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Position;
import com.example.interview.domain.PositionFile;
import com.example.interview.domain.PositionImage;
import com.example.interview.domain.PositionStatus;
import com.example.interview.mapper.EvalCriterionMapper;
import com.example.interview.mapper.PositionFileMapper;
import com.example.interview.mapper.PositionImageMapper;
import com.example.interview.mapper.PositionMapper;
import com.example.interview.resume.PdfTextExtractor;
import com.example.interview.resume.ResumeProcessingException;
import com.example.interview.web.dto.PositionSummary;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PositionService {

    static final int TITLE_MAX = 200;
    static final int IMAGE_MAX_COUNT = 5;
    static final int FILE_MAX_COUNT = 5;
    static final long IMAGE_MAX_BYTES = 5L * 1024 * 1024;

    private final PositionMapper positionMapper;
    private final PositionImageMapper imageMapper;
    private final PositionFileMapper fileMapper;
    private final EvalCriterionMapper criterionMapper;
    private final AiClient aiClient;
    private final PromptLoader promptLoader;
    private final PdfTextExtractor pdfTextExtractor;
    private final TransactionTemplate transactionTemplate;

    public Position get(Long id) {
        Position p = positionMapper.findById(id);
        if (p == null) {
            throw new NotFoundException("포지션을 찾을 수 없습니다: " + id);
        }
        return p;
    }

    public List<PositionSummary> recent(int limit) {
        return positionMapper.findRecent(limit);
    }

    /** 제목은 우선 정보글 첫 줄로 두고, 평가항목 추출 때 AI가 준 제목으로 바꾼다. */
    public Long create(String jdText) {
        return create(jdText, List.of());
    }

    /**
     * 설명글과 이미지 중 하나 이상이 있어야 한다. 이미지 형식·크기를 먼저 검사해서, 잘못된 입력이면 아무것도 저장하지 않는다.
     */
    public Long create(String jdText, List<MultipartFile> files) {
        StringBuilder sb = new StringBuilder(jdText == null ? "" : jdText.strip());
        List<PositionFile> originals = new ArrayList<>();
        List<PositionImage> images = readFiles(files, sb, originals);
        String text = sb.toString().strip();
        if (text.isEmpty() && images.isEmpty()) {
            throw new BusinessException("포지션 설명이나 파일을 넣어 주세요.");
        }
        return transactionTemplate.execute(status -> {
            Position p = new Position();
            p.setTitle(text.isEmpty() ? "포지션" : firstLineTitle(text));
            p.setJdText(text);
            p.setStatus(PositionStatus.DRAFT);
            positionMapper.insert(p);
            for (PositionImage image : images) {
                image.setPositionId(p.getId());
                imageMapper.insert(image);
            }
            for (PositionFile file : originals) {
                file.setPositionId(p.getId());
                fileMapper.insert(file);
            }
            return p.getId();
        });
    }

    public List<EvalCriterion> criteria(Long positionId) {
        return criterionMapper.findByPositionId(positionId);
    }

    public List<Long> imageIds(Long positionId) {
        return imageMapper.findIdsByPositionId(positionId);
    }

    /** 올린 원본 파일 목록 (바이트 없이) */
    public List<PositionFile> files(Long positionId) {
        return fileMapper.findByPositionId(positionId);
    }

    public PositionFile file(Long positionId, Long fileId) {
        PositionFile file = fileMapper.findById(fileId);
        if (file == null || !file.getPositionId().equals(positionId)) {
            throw new NotFoundException("파일을 찾을 수 없습니다: " + fileId);
        }
        return file;
    }

    public PositionImage image(Long positionId, Long imageId) {
        PositionImage image = imageMapper.findById(imageId);
        if (image == null || !image.getPositionId().equals(positionId)) {
            throw new NotFoundException("이미지를 찾을 수 없습니다: " + imageId);
        }
        return image;
    }

    /**
     * AI 호출 ①: 정보글(과 이미지)에서 평가항목을 추출해 저장하고 포지션을 확정 상태로 둔다. 기존 항목은 교체한다.
     *
     * @return 저장한 항목 수
     */
    public int extractCriteria(Long positionId) {
        Position p = get(positionId);
        List<AiImage> images = imageMapper.findByPositionId(positionId).stream()
                .map(i -> new AiImage(i.getContentType(), i.getImageData()))
                .toList();
        String userContent = "<job_posting>\n" + p.getJdText() + "\n</job_posting>"
                + (images.isEmpty() ? "" : "\n\n첨부 이미지 " + images.size() + "장도 포지션 설명입니다.");
        // AI 호출은 오래 걸리므로 DB 트랜잭션 밖에서 실행한다
        CriteriaResult result = aiClient.call(AiPurpose.CRITERIA_EXTRACT, positionId,
                promptLoader.load("criteria_extract"), userContent, images, CriteriaResult.class,
                CriteriaResult::validate);

        return transactionTemplate.execute(status -> {
            criterionMapper.deleteByPositionId(positionId);
            int order = 1;
            for (CriteriaResult.Item item : result.getCriteria()) {
                EvalCriterion c = new EvalCriterion();
                c.setPositionId(positionId);
                c.setName(truncate(item.getName().strip(), 200));
                c.setDescription(item.getDescription());
                c.setCategory(CriterionCategory.valueOf(item.getCategory()));
                c.setResumeAssessableYn(Boolean.TRUE.equals(item.getResumeAssessable()) ? "Y" : "N");
                c.setSource(CriterionSource.AI);
                c.setSortOrder(order++);
                criterionMapper.insert(c);
            }
            String title = result.getPositionTitle();
            if (title != null && !title.isBlank()) {
                positionMapper.updateTitle(positionId, truncate(title.strip(), TITLE_MAX));
            }
            if (!images.isEmpty()) {
                String imageText = result.getImageText();
                positionMapper.updateImageText(positionId,
                        imageText == null || imageText.isBlank() ? null : imageText.strip());
            }
            positionMapper.updateHiring(positionId,
                    truncate(blankToNull(result.getRequiredExperience()), 300),
                    truncate(blankToNull(result.getRequiredEducation()), 300),
                    truncate(blankToNull(result.getRequiredMajor()), 300));
            positionMapper.updateStatus(positionId, PositionStatus.CONFIRMED);
            return result.getCriteria().size();
        });
    }

    /** 경력·학력·학과 조건을 사용자가 직접 고친다. 비우면 조건 없음 */
    public void updateConditions(Long positionId, String requiredExperience, String requiredEducation,
            String requiredMajor) {
        get(positionId);
        positionMapper.updateConditions(positionId, truncate(blankToNull(requiredExperience), 300),
                truncate(blankToNull(requiredEducation), 300), truncate(blankToNull(requiredMajor), 300));
    }

    public EvalCriterion addCriterion(Long positionId, String name, String description) {
        get(positionId);
        EvalCriterion c = new EvalCriterion();
        c.setPositionId(positionId);
        c.setName(requireName(name));
        c.setDescription(blankToNull(description));
        c.setCategory(CriterionCategory.ETC);
        c.setResumeAssessableYn("Y");
        c.setSource(CriterionSource.MANUAL);
        c.setSortOrder(criterionMapper.maxSortOrder(positionId) + 1);
        criterionMapper.insert(c);
        positionMapper.updateStatus(positionId, PositionStatus.CONFIRMED);
        return c;
    }

    public String rename(Long positionId, String title) {
        get(positionId);
        if (title == null || title.isBlank()) {
            throw new BusinessException("포지션 이름을 입력하세요.");
        }
        String t = truncate(title.strip(), TITLE_MAX);
        positionMapper.rename(positionId, t);
        return t;
    }

    public void updateCriterion(Long positionId, Long criterionId, String name, String description) {
        EvalCriterion c = criterion(positionId, criterionId);
        c.setName(requireName(name));
        c.setDescription(blankToNull(description));
        criterionMapper.update(c);
    }

    public void updateExclusions(Long positionId, boolean excludeCareer, boolean excludeEducation) {
        get(positionId);
        positionMapper.updateExclusions(positionId, excludeCareer, excludeEducation);
    }

    public void deleteCriterion(Long positionId, Long criterionId) {
        criterionMapper.delete(criterion(positionId, criterionId).getId());
    }

    private EvalCriterion criterion(Long positionId, Long criterionId) {
        EvalCriterion c = criterionMapper.findById(criterionId);
        if (c == null || !c.getPositionId().equals(positionId)) {
            throw new NotFoundException("평가 기준을 찾을 수 없습니다: " + criterionId);
        }
        return c;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("평가 기준 이름을 입력하세요.");
        }
        return truncate(name.strip(), 200);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public void delete(Long positionId) {
        positionMapper.delete(positionId);
    }

    /**
     * 첨부 파일을 읽는다. 이미지는 그대로 AI에 보내고, PDF·텍스트 파일은 글자를 뽑아 text 뒤에 붙인다.
     * 글자가 거의 없는 스캔 PDF는 페이지를 이미지로 그려 남은 이미지 자리만큼 보낸다.
     * 원본은 다시 내려받을 수 있게 originals 에 담는다.
     */
    private List<PositionImage> readFiles(List<MultipartFile> files, StringBuilder text, List<PositionFile> originals) {
        List<PositionImage> images = new ArrayList<>();
        if (files == null) {
            return images;
        }
        List<MultipartFile> present = files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (present.size() > FILE_MAX_COUNT) {
            throw new BusinessException("파일은 " + FILE_MAX_COUNT + "개까지 넣을 수 있습니다.");
        }
        for (MultipartFile f : present) {
            String name = f.getOriginalFilename() == null ? "파일" : f.getOriginalFilename();
            byte[] data = bytes(f, name);
            String imageType = imageType(data);
            if (imageType != null) {
                if (data.length > IMAGE_MAX_BYTES) {
                    throw new BusinessException("이미지는 5MB 이하여야 합니다: " + name);
                }
                addImage(images, imageType, data, name);
                addOriginal(originals, name, imageType, data);
            } else if (isPdf(data)) {
                String pdfText;
                try {
                    pdfText = pdfTextExtractor.extractAny(data);
                    if (!PdfTextExtractor.hasEnoughText(pdfText)) {
                        List<byte[]> pages = pdfTextExtractor.renderPages(data, IMAGE_MAX_COUNT - images.size());
                        if (pages.isEmpty()) {
                            throw new BusinessException("이미지는 " + IMAGE_MAX_COUNT + "장까지 넣을 수 있습니다: " + name);
                        }
                        pages.forEach(page -> addImage(images, "image/png", page, name));
                        addOriginal(originals, name, "application/pdf", data);
                        continue;
                    }
                } catch (ResumeProcessingException e) {
                    throw new BusinessException(e.getMessage() + " (" + name + ")");
                }
                appendFile(text, name, pdfText);
                addOriginal(originals, name, "application/pdf", data);
            } else if (isTextFile(name)) {
                appendFile(text, name, decodeText(data));
                addOriginal(originals, name, name.toLowerCase().endsWith(".md") ? "text/markdown" : "text/plain", data);
            } else {
                throw new BusinessException("PDF, TXT, MD, 이미지 파일만 넣을 수 있습니다: " + name);
            }
        }
        return images;
    }

    private static void addOriginal(List<PositionFile> originals, String name, String type, byte[] data) {
        PositionFile file = new PositionFile();
        file.setFileName(fileName(name));
        file.setContentType(type);
        file.setFileData(data);
        file.setSortOrder(originals.size() + 1);
        originals.add(file);
    }

    /** 브라우저가 경로까지 붙여 보내는 경우가 있어 마지막 이름만 남긴다 */
    static String fileName(String name) {
        String base = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).strip();
        return truncate(base.isEmpty() ? "파일" : base, 255);
    }

    private static void addImage(List<PositionImage> images, String type, byte[] data, String name) {
        if (images.size() == IMAGE_MAX_COUNT) {
            throw new BusinessException("이미지는 " + IMAGE_MAX_COUNT + "장까지 넣을 수 있습니다: " + name);
        }
        PositionImage image = new PositionImage();
        image.setContentType(type);
        image.setImageData(data);
        image.setSortOrder(images.size() + 1);
        images.add(image);
    }

    private static byte[] bytes(MultipartFile f, String name) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw new BusinessException("파일을 읽지 못했습니다: " + name);
        }
    }

    private static void appendFile(StringBuilder text, String name, String content) {
        String body = content.strip();
        if (body.isEmpty()) {
            return;
        }
        if (!text.isEmpty()) {
            text.append("\n\n");
        }
        text.append("[").append(name).append("]\n").append(body);
    }

    private static boolean isPdf(byte[] d) {
        return d.length >= 5 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F' && d[4] == '-';
    }

    private static boolean isTextFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md");
    }

    /** UTF-8이 아니면 윈도우 한글(CP949)로 읽는다. */
    static String decodeText(byte[] data) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString().replace("﻿", "");
        } catch (CharacterCodingException e) {
            return new String(data, Charset.forName("MS949"));
        }
    }

    /** 파일 이름이나 브라우저가 준 형식 대신 실제 바이트로 이미지 형식을 판단한다. */
    static String imageType(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') {
            return "image/gif";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    static String firstLineTitle(String jdText) {
        for (String line : jdText.split("\\R")) {
            String s = line.strip();
            if (!s.isEmpty()) {
                return truncate(s, 60);
            }
        }
        return "포지션";
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
