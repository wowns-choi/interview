package com.example.interview.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 가상 이력서(src/test/resources/samples/*.txt)를 한글 PDF로 만들어 build/sample-resumes/ 에 저장하고,
 * 추출 → 마스킹까지 왕복 검증한다. 만든 PDF는 화면에서 수동 테스트할 때 업로드용으로 쓴다.
 * 한글 글꼴(맑은 고딕)이 없는 환경에서는 건너뛴다.
 */
class SampleResumePdfTest {

    private static final File KOREAN_FONT = new File("C:/Windows/Fonts/malgun.ttf");

    @ParameterizedTest
    @CsvSource({ "resume-rich, 김민준", "resume-sparse, 박서연", "resume-injection, 이도현" })
    void generateAndRoundTrip(String sample, String name) throws IOException {
        assumeTrue(KOREAN_FONT.exists(), "한글 글꼴이 없어 건너뜀");
        String text = PiiMaskerTest.read("samples/" + sample + ".txt");

        byte[] pdf = toPdf(text);
        Path dir = Path.of("build", "sample-resumes");
        Files.createDirectories(dir);
        Files.write(dir.resolve(sample + ".pdf"), pdf);

        String extracted = new PdfTextExtractor().extract(pdf);
        assertThat(extracted).contains(name);
        String masked = new PiiMasker().mask(extracted, name);
        assertThat(masked).doesNotContain(name).contains("[이름]", "[이메일]", "[전화번호]");
    }

    private static byte[] toPdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument(); var out = new java.io.ByteArrayOutputStream()) {
            PDType0Font font = PDType0Font.load(doc, KOREAN_FONT);
            float fontSize = 10.5f;
            float leading = 16f;
            float margin = 50f;
            PDPage page = null;
            PDPageContentStream cs = null;
            float y = 0;
            for (String line : text.split("\\R")) {
                if (cs == null || y < margin) {
                    if (cs != null) {
                        cs.endText();
                        cs.close();
                    }
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    y = page.getMediaBox().getHeight() - margin;
                    cs.newLineAtOffset(margin, y);
                }
                cs.showText(line);
                cs.newLineAtOffset(0, -leading);
                y -= leading;
            }
            if (cs != null) {
                cs.endText();
                cs.close();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
