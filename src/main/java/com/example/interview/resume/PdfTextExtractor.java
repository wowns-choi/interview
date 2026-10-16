package com.example.interview.resume;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** PDF에서 페이지 순서대로 텍스트를 추출한다. 원본 PDF는 어디에도 저장하지 않는다. */
@Component
public class PdfTextExtractor {

    static final int MIN_TEXT_LENGTH = 50;

    public String extract(byte[] pdfBytes) {
        String normalized = extractAny(pdfBytes);
        if (!hasEnoughText(normalized)) {
            throw new ResumeProcessingException("스캔 이미지 PDF는 지원하지 않습니다. (추출된 텍스트가 " + MIN_TEXT_LENGTH + "자 미만)");
        }
        return normalized;
    }

    /** 글자 수 검사 없이 텍스트를 뽑는다. 스캔 PDF면 빈 문자열에 가깝다. */
    public String extractAny(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new ResumeProcessingException("PDF 파일이 비어 있습니다.");
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            return normalize(stripper.getText(document));
        } catch (InvalidPasswordException e) {
            throw new ResumeProcessingException("암호가 걸린 PDF는 지원하지 않습니다.", e);
        } catch (IOException e) {
            throw new ResumeProcessingException("PDF 파일을 읽을 수 없습니다. 올바른 PDF인지 확인하세요.", e);
        }
    }

    public static boolean hasEnoughText(String text) {
        return text.replaceAll("\\s", "").length() >= MIN_TEXT_LENGTH;
    }

    /** 앞에서부터 최대 maxPages쪽을 PNG로 그린다 (스캔 PDF를 AI 이미지 입력으로 넘길 때). */
    public List<byte[]> renderPages(byte[] pdfBytes, int maxPages) {
        List<byte[]> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int count = Math.min(maxPages, document.getNumberOfPages());
            for (int i = 0; i < count; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 110, ImageType.RGB);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                pages.add(out.toByteArray());
            }
        } catch (IOException e) {
            throw new ResumeProcessingException("PDF 파일을 읽을 수 없습니다. 올바른 PDF인지 확인하세요.", e);
        }
        return pages;
    }

    /** 줄 끝 공백과 3줄 이상 연속된 빈 줄을 정리한다. 문장 내용은 바꾸지 않는다. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\u00A0]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
