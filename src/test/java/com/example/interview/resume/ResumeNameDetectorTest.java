package com.example.interview.resume;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class ResumeNameDetectorTest {

    private final ResumeNameDetector detector = new ResumeNameDetector();

    @Test
    void detectsLabeledName() {
        assertThat(detector.detect("인적사항\n성명: 홍길동\n연락처 010-0000-0000")).contains("홍길동");
        assertThat(detector.detect("이름 김 민 준")).contains("김민준");
    }

    @Test
    void detectsNameOnlyLineNearTop() {
        assertThat(detector.detect("이력서\n\n박서연\n010 9876 5432")).contains("박서연");
    }

    @Test
    void ignoresHeadingsAndLongLines() {
        assertThat(detector.detect("이력서\n경력요약\n백엔드 개발자 6년차입니다")).isEmpty();
        assertThat(detector.detect("")).isEmpty();
    }

    @Test
    void detectsNamesInSamples() throws IOException {
        assertThat(detector.detect(PiiMaskerTest.read("samples/resume-rich.txt"))).contains("김민준");
        assertThat(detector.detect(PiiMaskerTest.read("samples/resume-sparse.txt"))).contains("박서연");
        assertThat(detector.detect(PiiMaskerTest.read("samples/resume-injection.txt"))).contains("이도현");
    }
}
