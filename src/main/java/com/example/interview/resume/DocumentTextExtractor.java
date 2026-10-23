package com.example.interview.resume;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.swing.text.rtf.RTFEditorKit;

import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;
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
        PDF, DOC, DOCX, PPT, PPTX, HWP, HWPX
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
                case DOC -> doc(bytes);
                case DOCX -> docx(bytes);
                case PPT -> slides(new HSLFSlideShow(new ByteArrayInputStream(bytes)));
                case PPTX -> slides(new XMLSlideShow(new ByteArrayInputStream(bytes)));
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
            case "doc" -> Kind.DOC;
            case "docx" -> Kind.DOCX;
            case "ppt" -> Kind.PPT;
            case "pptx" -> Kind.PPTX;
            case "hwp" -> Kind.HWP;
            case "hwpx" -> Kind.HWPX;
            default -> throw new ResumeProcessingException(
                    filename + ": PDF, 워드(.doc, .docx), 파워포인트(.ppt, .pptx), 한글(.hwp, .hwpx) 파일만 지원합니다.");
        };
    }

    /**
     * .doc는 진짜 워드 파일만이 아니다. 사람인·잡코리아의 "워드로 저장"은 HTML을 .doc 이름으로 내려 주고,
     * RTF나 .docx를 .doc로 바꿔 단 파일도 있어서 앞 바이트로 형식을 가린다.
     */
    private static String doc(byte[] bytes) throws Exception {
        if (startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0)) {
            return word97(bytes);
        }
        if (startsWith(bytes, 0x50, 0x4B)) {
            return docx(bytes);
        }
        String head = new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.ISO_8859_1)
                .stripLeading().toLowerCase(Locale.ROOT);
        if (head.startsWith("{\\rtf")) {
            return rtf(bytes);
        }
        if (head.contains("<html") || head.contains("<!doctype html") || head.contains("<table") || head.contains("<body")) {
            return html(bytes);
        }
        return word97(bytes);
    }

    private static boolean startsWith(byte[] bytes, int... magic) {
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((bytes[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static String word97(byte[] bytes) throws IOException {
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(bytes));
                WordExtractor extractor = new WordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private static String rtf(byte[] bytes) throws Exception {
        RTFEditorKit kit = new RTFEditorKit();
        javax.swing.text.Document doc = kit.createDefaultDocument();
        kit.read(new ByteArrayInputStream(bytes), doc, 0);
        return doc.getText(0, doc.getLength());
    }

    /** 글자 인코딩은 문서의 meta charset을 따른다. 표는 칸을 " | ", 행을 줄바꿈으로 잇는다. */
    private static String html(byte[] bytes) throws IOException {
        Document page = Jsoup.parse(new ByteArrayInputStream(bytes), null, "");
        page.select("script, style, head, noscript, template").remove();
        StringBuilder sb = new StringBuilder();
        page.body().traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode t) {
                    sb.append(t.text());
                } else if (node instanceof Element e) {
                    if (e.nameIs("td") || e.nameIs("th")) {
                        if (e.elementSiblingIndex() > 0) {
                            sb.append(" | ");
                        }
                    } else if (e.nameIs("br") || e.isBlock()) {
                        newline(sb);
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element e && e.isBlock() && !e.nameIs("td") && !e.nameIs("th")) {
                    newline(sb);
                }
            }
        });
        return sb.toString().replaceAll("[ \t\u00A0]+\n", "\n").replaceAll("\n{3,}", "\n\n");
    }

    private static void newline(StringBuilder sb) {
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
    }

    private static String docx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    /** 슬라이드마다 "[슬라이드 n]" 줄을 붙인다. 발표자 노트도 읽는다. */
    private static <S extends Shape<S, P>, P extends TextParagraph<S, P, ?>> String slides(SlideShow<S, P> opened) throws IOException {
        try (SlideShow<S, P> show = opened) {
            SlideShowExtractor<S, P> extractor = new SlideShowExtractor<>(show);
            extractor.setNotesByDefault(true);
            extractor.setCloseFilesystem(false);
            StringBuilder sb = new StringBuilder();
            int n = 1;
            for (Slide<S, P> slide : show.getSlides()) {
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
