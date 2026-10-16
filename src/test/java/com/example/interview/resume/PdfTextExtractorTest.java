package com.example.interview.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void extractsTextInPageOrder() throws IOException {
        byte[] pdf = pdf(List.of(
                List.of("Page one: Backend developer with 6 years of experience.", "Java, Spring Boot, MySQL"),
                List.of("Page two: Improved batch processing of 1 million orders per day.")));
        String text = extractor.extract(pdf);
        assertThat(text).contains("Backend developer", "Spring Boot", "1 million orders");
        assertThat(text.indexOf("Page one")).isLessThan(text.indexOf("Page two"));
    }

    @Test
    void rejectsPdfWithoutText() throws IOException {
        byte[] pdf = pdf(List.of(List.of()));
        assertThatThrownBy(() -> extractor.extract(pdf))
                .isInstanceOf(ResumeProcessingException.class)
                .hasMessageContaining("스캔 이미지 PDF는 지원하지 않습니다");
    }

    @Test
    void rejectsTooShortText() throws IOException {
        byte[] pdf = pdf(List.of(List.of("Short text")));
        assertThatThrownBy(() -> extractor.extract(pdf)).isInstanceOf(ResumeProcessingException.class);
    }

    @Test
    void rejectsNonPdf() {
        assertThatThrownBy(() -> extractor.extract("not a pdf".getBytes()))
                .isInstanceOf(ResumeProcessingException.class)
                .hasMessageContaining("PDF 파일을 읽을 수 없습니다");
    }

    static byte[] pdf(List<List<String>> pages) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (List<String> lines : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    if (!lines.isEmpty()) {
                        cs.beginText();
                        cs.setFont(font, 12);
                        cs.newLineAtOffset(50, 700);
                        for (String line : lines) {
                            cs.showText(line);
                            cs.newLineAtOffset(0, -18);
                        }
                        cs.endText();
                    }
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
