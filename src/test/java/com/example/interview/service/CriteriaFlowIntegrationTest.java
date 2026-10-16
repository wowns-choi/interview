package com.example.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

import com.example.interview.ai.AiClient;
import com.example.interview.ai.AiImage;
import com.example.interview.ai.dto.CriteriaResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.Position;
import com.example.interview.domain.PositionStatus;

/** 평가 기준 탭: 이미지만으로 포지션을 만들고 AI ①에 이미지가 전달되는지 확인한다. */
@SpringBootTest
class CriteriaFlowIntegrationTest {

    static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

    @MockitoBean
    AiClient aiClient;

    @Autowired
    PositionService positionService;

    @Test
    @SuppressWarnings("unchecked")
    void extractsCriteriaFromImageOnlyPosition() {
        CriteriaResult result = new CriteriaResult();
        result.setPositionTitle("데이터 엔지니어");
        result.setImageText("주요 업무: 데이터 파이프라인 구축");
        CriteriaResult.Item item = new CriteriaResult.Item();
        item.setName("Spark 사용 경험");
        item.setRequirement("MUST");
        item.setCategory("TECH");
        item.setResumeAssessable(true);
        result.setCriteria(List.of(item));
        when(aiClient.call(eq(AiPurpose.CRITERIA_EXTRACT), anyLong(), anyString(), anyString(), any(List.class),
                eq(CriteriaResult.class), any())).thenReturn(result);

        Long id = positionService.create("  ", List.of(png("capture.png")));
        positionService.extractCriteria(id);

        ArgumentCaptor<List<AiImage>> images = ArgumentCaptor.forClass(List.class);
        verify(aiClient).call(eq(AiPurpose.CRITERIA_EXTRACT), eq(id), anyString(), anyString(), images.capture(),
                eq(CriteriaResult.class), any());
        assertThat(images.getValue()).singleElement()
                .satisfies(i -> assertThat(i.contentType()).isEqualTo("image/png"));

        Position p = positionService.get(id);
        assertThat(p.getStatus()).isEqualTo(PositionStatus.CONFIRMED);
        assertThat(p.getTitle()).isEqualTo("데이터 엔지니어");
        assertThat(p.getPostingText()).isEqualTo("주요 업무: 데이터 파이프라인 구축");
        assertThat(positionService.criteria(id)).extracting("name").containsExactly("Spark 사용 경험");
        List<Long> imageIds = positionService.imageIds(id);
        assertThat(imageIds).hasSize(1);
        assertThat(positionService.image(id, imageIds.get(0)).getImageData()).isEqualTo(PNG);
        assertThatThrownBy(() -> positionService.image(id + 1000, imageIds.get(0))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void readsTextFromPdfAndTxtFiles() throws Exception {
        String english = "Backend engineer. Requirements: Java, Spring, five years of large scale traffic experience.";
        MultipartFile pdf = new MockMultipartFile("files", "posting.pdf", "application/pdf", pdf(english));
        MultipartFile txt = new MockMultipartFile("files", "우대.txt", "text/plain", "우대사항: Kafka".getBytes("MS949"));

        Long id = positionService.create("직접 쓴 설명", List.of(pdf, txt));

        Position p = positionService.get(id);
        assertThat(p.getJdText()).startsWith("직접 쓴 설명").contains("[posting.pdf]", "large scale traffic",
                "[우대.txt]", "우대사항: Kafka");
        assertThat(positionService.imageIds(id)).isEmpty();
    }

    @Test
    void scannedPdfIsSentAsPageImages() throws Exception {
        MultipartFile scanned = new MockMultipartFile("files", "scan.pdf", "application/pdf", pdf(null));

        Long id = positionService.create("", List.of(scanned));

        assertThat(positionService.imageIds(id)).hasSize(1);
        assertThat(positionService.image(id, positionService.imageIds(id).get(0)).getContentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsEmptyInputAndUnknownFiles() {
        assertThatThrownBy(() -> positionService.create("", List.of())).isInstanceOf(BusinessException.class)
                .hasMessageContaining("포지션 설명이나 파일");
        MultipartFile fake = new MockMultipartFile("files", "a.png", "image/png", "not an image".getBytes());
        assertThatThrownBy(() -> positionService.create("공고", List.of(fake))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미지, PDF, TXT");
        List<MultipartFile> six = List.of(png("1"), png("2"), png("3"), png("4"), png("5"), png("6"));
        assertThatThrownBy(() -> positionService.create("공고", six)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("5개");
    }

    /** text가 null이면 글자 없는 빈 페이지 (스캔 PDF 흉내) */
    private static byte[] pdf(String text) throws java.io.IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            if (text != null) {
                try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 10);
                    cs.newLineAtOffset(40, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("images", name, "image/png", PNG);
    }
}
