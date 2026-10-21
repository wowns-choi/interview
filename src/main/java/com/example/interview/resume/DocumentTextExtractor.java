package com.example.interview.resume;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.reader.HWPXReader;
import kr.dogfoot.hwpxlib.tool.textextractor.TextMarks;

import lombok.RequiredArgsConstructor;

/**
 * 지원자가 낸 파일(이력서·포트폴리오)에서 글자를 뽑는다. 형식은 파일 확장자로 정한다.
 * 그림 속 글자는 읽지 않는다. 원본 파일은 저장하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class DocumentTextExtractor {

    public enum Kind {
        PDF, DOCX, PPTX, HWP, HWPX
    }

    public record Extracted(Kind kind, String text) {
    }

    private final PdfTextExtractor pdfTextExtractor;

    public Extracted extract(String filename, byte[] bytes) {
        Kind kind = kindOf(filename);
        String text;
        try {
            text = switch (kind) {
                case PDF -> pdfTextExtractor.extract(bytes);
                case DOCX -> docx(bytes);
                case PPTX -> pptx(bytes);
                case HWP -> hwp(bytes);
                case HWPX -> hwpx(bytes);
            };
        } catch (ResumeProcessingException e) {
            throw new ResumeProcessingException(filename + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ResumeProcessingException(filename + ": 파일을 읽을 수 없습니다. 암호가 걸렸거나 손상된 파일인지 확인하세요.", e);
        }
        text = PdfTextExtractor.normalize(text);
        if (text.isBlank()) {
            throw new ResumeProcessingException(filename + ": 글자를 찾지 못했습니다. 그림만 있는 파일은 지원하지 않습니다.");
        }
        return new Extracted(kind, text);
    }

    public static Kind kindOf(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        return switch (ext) {
            case "pdf" -> Kind.PDF;
            case "docx" -> Kind.DOCX;
            case "pptx" -> Kind.PPTX;
            case "hwp" -> Kind.HWP;
            case "hwpx" -> Kind.HWPX;
            case "doc", "ppt" -> throw new ResumeProcessingException(
                    filename + ": 옛 형식(." + ext + ")은 지원하지 않습니다. ." + ext + "x로 다시 저장해 주세요.");
            default -> throw new ResumeProcessingException(
                    filename + ": PDF, 워드(.docx), 파워포인트(.pptx), 한글(.hwp, .hwpx) 파일만 지원합니다.");
        };
    }

    private static String docx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    /** 슬라이드마다 "[슬라이드 n]" 줄을 붙인다. 발표자 노트도 읽는다. */
    private static String pptx(byte[] bytes) throws IOException {
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            SlideShowExtractor<XSLFShape, XSLFTextParagraph> extractor = new SlideShowExtractor<>(show);
            extractor.setNotesByDefault(true);
            extractor.setCloseFilesystem(false);
            StringBuilder sb = new StringBuilder();
            int n = 1;
            for (XSLFSlide slide : show.getSlides()) {
                String text = extractor.getText(slide).strip();
                if (!text.isEmpty()) {
                    sb.append("[슬라이드 ").append(n).append("]\n").append(text).append("\n\n");
                }
                n++;
            }
            return sb.toString();
        }
    }

    private static String hwp(byte[] bytes) throws Exception {
        HWPFile file = HWPReader.fromInputStream(new ByteArrayInputStream(bytes));
        return kr.dogfoot.hwplib.tool.textextractor.TextExtractor.extract(file,
                kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod.InsertControlTextBetweenParagraphText);
    }

    /** hwpxlib은 파일 경로로만 읽을 수 있어 임시 파일을 쓰고 바로 지운다. */
    private static String hwpx(byte[] bytes) throws Exception {
        Path temp = Files.createTempFile("resume-", ".hwpx");
        try {
            Files.write(temp, bytes);
            HWPXFile file = HWPXReader.fromFile(temp.toFile());
            TextMarks marks = new TextMarks()
                    .paraSeparatorAnd("\n")
                    .lineBreakAnd("\n")
                    .tabAnd("\t")
                    .tableRowSeparatorAnd("\n")
                    .tableCellSeparatorAnd(" | ");
            return kr.dogfoot.hwpxlib.tool.textextractor.TextExtractor.extract(file,
                    kr.dogfoot.hwpxlib.tool.textextractor.TextExtractMethod.InsertControlTextBetweenParagraphText,
                    false, marks);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
