package com.example.interview.resume;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    @Test
    void masksName() {
        assertThat(masker.mask("지원자 김민준 입니다. 김 민 준", "김민준"))
                .isEqualTo("지원자 [이름] 입니다. [이름]");
    }

    @Test
    void masksMobilePhonesWithVariousSeparators() {
        assertThat(masker.mask("010-1234-5678", null)).isEqualTo("[전화번호]");
        assertThat(masker.mask("010 1234 5678", null)).isEqualTo("[전화번호]");
        assertThat(masker.mask("010.1234.5678", null)).isEqualTo("[전화번호]");
        assertThat(masker.mask("01012345678", null)).isEqualTo("[전화번호]");
        assertThat(masker.mask("+82 10-1234-5678", null)).isEqualTo("[전화번호]");
        assertThat(masker.mask("전화 02-123-4567", null)).isEqualTo("전화 [전화번호]");
    }

    @Test
    void masksEmail() {
        assertThat(masker.mask("메일: minjun.kim+dev@example.co.kr 로 연락", null))
                .isEqualTo("메일: [이메일] 로 연락");
    }

    @Test
    void masksResidentRegistrationNumber() {
        assertThat(masker.mask("주민번호 950505-1234567", null)).isEqualTo("주민번호 [주민번호]");
        assertThat(masker.mask("950505 - 1******", null)).isEqualTo("[주민번호]");
    }

    @Test
    void masksLabeledBirthDateButKeepsCareerDates() {
        assertThat(masker.mask("생년월일: 1992년 3월 14일", null)).isEqualTo("생년월일: [생년월일]");
        assertThat(masker.mask("생일 2000.11.02", null)).isEqualTo("생일 [생년월일]");
        assertThat(masker.mask("1992.03.14생", null)).isEqualTo("[생년월일]");
        // 경력 기간과 프로젝트 날짜는 근거로 써야 하므로 남긴다
        assertThat(masker.mask("2021.03 ~ 현재, 배포일 2023.05.10", null))
                .isEqualTo("2021.03 ~ 현재, 배포일 2023.05.10");
    }

    @Test
    void masksDetailedAddressButKeepsRegion() {
        assertThat(masker.mask("주소: 서울특별시 강서구 화곡로 123-4, 101동 1203호", null))
                .isEqualTo("주소: 서울특별시 강서구 [주소]");
        assertThat(masker.mask("경기도 성남시 분당구 판교역로 235", null))
                .isEqualTo("경기도 성남시 분당구 [주소]");
        assertThat(masker.mask("서울 강서구 화곡동 123-45번지", null))
                .isEqualTo("서울 강서구 화곡동 [주소]");
    }

    @Test
    void doesNotMaskOrdinaryNumbers() {
        String text = "일 100만 건 처리, 응답속도 40% 개선, 초당 3,000건, 4인 파트, 프로젝트로 3개월";
        assertThat(masker.mask(text, "홍길동")).isEqualTo(text);
    }

    @Test
    void masksSampleResume() throws IOException {
        String text = read("samples/resume-rich.txt");
        String masked = masker.mask(text, "김민준");
        assertThat(masked)
                .doesNotContain("김민준", "010-2345-6789", "minjun.kim.dev@example.com", "1992년 3월 14일", "화곡로 123-4")
                .contains("[이름]", "[전화번호]", "[이메일]", "[생년월일]", "[주소]")
                .contains("일 100만 건 주문 데이터 배치 처리 개선");
    }

    @Test
    void masksInjectionSample() throws IOException {
        String masked = masker.mask(read("samples/resume-injection.txt"), "이도현");
        assertThat(masked)
                .doesNotContain("이도현", "010.5555.1234", "950505-1234567", "판교역로 235")
                .contains("[주민번호]");
    }

    static String read(String path) throws IOException {
        try (InputStream in = PiiMaskerTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
