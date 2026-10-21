package com.example.interview.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;

import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import com.example.interview.resume.DocumentTextExtractor.Kind;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.writer.HWPWriter;
import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.writer.HWPXWriter;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor(new PdfTextExtractor());

    @Test
    void readsDocxParagraphsAndTables() throws Exception {
        byte[] bytes;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("김민준 백엔드 개발자");
            XWPFTable table = doc.createTable(1, 2);
            table.getRow(0).getCell(0).setText("기술");
            table.getRow(0).getCell(1).setText("Spring Batch");
            doc.write(out);
            bytes = out.toByteArray();
        }

        DocumentTextExtractor.Extracted result = extractor.extract("이력서.docx", bytes);

        assertThat(result.kind()).isEqualTo(Kind.DOCX);
        assertThat(result.text()).contains("김민준 백엔드 개발자", "Spring Batch");
    }

    @Test
    void readsPptxSlidesInOrderWithNotes() throws Exception {
        byte[] bytes;
        try (XMLSlideShow show = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            show.createSlide().createTextBox().setText("주문 시스템 개선 프로젝트");
            XSLFSlide second = show.createSlide();
            second.createTextBox().setText("p95 320ms → 190ms");
            XSLFNotes notes = show.getNotesSlide(second);
            for (XSLFTextShape shape : notes.getPlaceholders()) {
                if (shape.getTextType() == Placeholder.BODY) {
                    shape.setText("발표자 노트: nGrinder로 측정");
                }
            }
            show.write(out);
            bytes = out.toByteArray();
        }

        String text = extractor.extract("포트폴리오.pptx", bytes).text();

        assertThat(text).containsSubsequence("[슬라이드 1]", "주문 시스템 개선 프로젝트", "[슬라이드 2]", "p95 320ms → 190ms");
        assertThat(text).contains("nGrinder로 측정");
    }

    @Test
    void readsHwp() throws Exception {
        HWPFile hwp = kr.dogfoot.hwplib.tool.blankfilemaker.BlankFileMaker.make();
        Paragraph para = hwp.getBodyText().getSectionList().get(0).getParagraph(0);
        if (para.getText() == null) {
            para.createText();
        }
        para.getText().addString("한글 이력서 박서연");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HWPWriter.toStream(hwp, out);

        DocumentTextExtractor.Extracted result = extractor.extract("이력서.hwp", out.toByteArray());

        assertThat(result.kind()).isEqualTo(Kind.HWP);
        assertThat(result.text()).contains("한글 이력서 박서연");
    }

    @Test
    void readsHwpx() throws Exception {
        HWPXFile hwpx = kr.dogfoot.hwpxlib.tool.blankfilemaker.BlankFileMaker.make();
        hwpx.sectionXMLFileList().get(0).getPara(0).addNewRun().addNewT().addText("한글 이력서 이도현");

        DocumentTextExtractor.Extracted result = extractor.extract("이력서.HWPX", HWPXWriter.toBytes(hwpx));

        assertThat(result.kind()).isEqualTo(Kind.HWPX);
        assertThat(result.text()).contains("한글 이력서 이도현");
    }

    @Test
    void rejectsUnsupportedOrEmptyFiles() throws Exception {
        assertThatThrownBy(() -> extractor.extract("old.ppt", new byte[] { 1 }))
                .isInstanceOf(ResumeProcessingException.class).hasMessageContaining(".pptx로 다시 저장");
        assertThatThrownBy(() -> extractor.extract("memo.txt", new byte[] { 1 }))
                .isInstanceOf(ResumeProcessingException.class).hasMessageContaining("memo.txt");
        assertThatThrownBy(() -> extractor.extract("broken.docx", new byte[] { 1, 2, 3 }))
                .isInstanceOf(ResumeProcessingException.class).hasMessageContaining("broken.docx", "읽을 수 없습니다");

        byte[] imageOnly;
        try (XMLSlideShow show = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            show.createSlide();
            show.write(out);
            imageOnly = out.toByteArray();
        }
        assertThatThrownBy(() -> extractor.extract("그림만.pptx", imageOnly))
                .isInstanceOf(ResumeProcessingException.class).hasMessageContaining("글자를 찾지 못했습니다");
    }
}
