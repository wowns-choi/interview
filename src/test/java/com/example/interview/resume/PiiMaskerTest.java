package com.example.interview.resume;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.dto.PiiResult;
import com.example.interview.ai.dto.PiiResult.Type;

class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    @Test
    void masksEveryOccurrenceIncludingSpacedName() {
        PiiMasker.Masked m = masker.mask("홍길동\n안녕하세요, 홍 길 동입니다. hong@example.com",
                List.of(item(Type.APPLICANT_NAME, "홍길동"), item(Type.EMAIL, "hong@example.com")));

        assertThat(m.text()).isEqualTo("[이름]\n안녕하세요, [이름]입니다. [이메일]");
        assertThat(m.applicantName()).contains("홍길동");
    }

    @Test
    void labelsByType() {
        String text = "부 남궁철 / 010-1234-5678 / 950101-1234567 / github.com/abc / 만 31세 / 화곡로 12 / 기혼";
        PiiMasker.Masked m = masker.mask(text, List.of(
                item(Type.OTHER_NAME, "남궁철"), item(Type.PHONE, "010-1234-5678"), item(Type.RRN, "950101-1234567"),
                item(Type.URL, "github.com/abc"), item(Type.BIRTH, "만 31세"), item(Type.ADDRESS, "화곡로 12"),
                item(Type.SENSITIVE, "기혼")));

        assertThat(m.text()).isEqualTo("부 [이름] / [전화번호] / [주민번호] / [링크] / [생년월일] / [주소] / [개인정보]");
        assertThat(m.applicantName()).as("지원자 이름이 없으면 비어 있음").isEmpty();
    }

    @Test
    void masksValuesWrittenWithDifferentSpacing() {
        PiiMasker.Masked m = masker.mask("직전 연봉 : 3,400 만원\n소속회사 (주)태화 이노베이션\n남, 1993.01.01 (33세)",
                List.of(item(Type.SALARY, "3,400만원"), item(Type.COMPANY, "태화이노베이션"), item(Type.SENSITIVE, "남"),
                        item(Type.BIRTH, "1993.01.01"), item(Type.BIRTH, "(33세)")));

        assertThat(m.text()).isEqualTo("직전 연봉 : [연봉]\n소속회사 (주)[회사]\n[개인정보], [생년월일] [생년월일]");
    }

    @Test
    void longerValueIsMaskedFirst() {
        PiiMasker.Masked m = masker.mask("github.com/hong-gildong 홍길동",
                List.of(item(Type.APPLICANT_NAME, "홍길동"), item(Type.URL, "github.com/hong-gildong")));

        assertThat(m.text()).isEqualTo("[링크] [이름]");
    }

    @Test
    void singleCharacterOnlyWhenStandalone() {
        PiiMasker.Masked m = masker.mask("성별: 남 / 남궁 프로젝트 / 남부지사",
                List.of(item(Type.SENSITIVE, "남")));

        assertThat(m.text()).isEqualTo("성별: [개인정보] / 남궁 프로젝트 / 남부지사");
    }

    @Test
    void ignoresValuesNotInText() {
        PiiMasker.Masked m = masker.mask("백엔드 개발자 김민준", List.of(
                item(Type.APPLICANT_NAME, "이도현"), item(Type.APPLICANT_NAME, "김민준")));

        assertThat(m.text()).isEqualTo("백엔드 개발자 [이름]");
        assertThat(m.applicantName()).as("원문에 있는 이름을 고름").contains("김민준");
    }

    @Test
    void keepsCareerContentAndPlaceholders() throws IOException {
        String text = read("samples/resume-rich.txt");
        PiiMasker.Masked m = masker.mask(text, List.of(
                item(Type.APPLICANT_NAME, "김민준"), item(Type.BIRTH, "1992년 3월 14일"),
                item(Type.PHONE, "010-2345-6789"), item(Type.EMAIL, "minjun.kim.dev@example.com"),
                item(Type.ADDRESS, "화곡로 123-4, 101동 1203호")));

        assertThat(m.text())
                .doesNotContain("김민준", "1992년 3월 14일", "010-2345-6789", "minjun.kim.dev", "화곡로")
                .contains("서울특별시 강서구 [주소]", "2021.03 ~ 현재", "(주)가상커머스", "가상대학교 컴퓨터공학과");
        assertThat(new PiiLeakChecker().find(m.text())).isEmpty();
    }

    static PiiResult.Item item(Type type, String text) {
        PiiResult.Item item = new PiiResult.Item();
        item.setType(type);
        item.setText(text);
        return item;
    }

    static String read(String path) throws IOException {
        try (InputStream in = PiiMaskerTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
