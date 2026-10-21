package com.example.interview.resume;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiLeakCheckerTest {

    private final PiiLeakChecker checker = new PiiLeakChecker();

    @Test
    void findsLeftoverContactAndRrn() {
        assertThat(checker.find("주민번호 950101-1234567")).containsExactly("주민번호");
        assertThat(checker.find("주민번호 950101-1******")).containsExactly("주민번호");
        assertThat(checker.find("메일 a.b@example.co.kr")).containsExactly("이메일");
        assertThat(checker.find("010-1234-5678")).containsExactly("휴대전화");
        assertThat(checker.find("01012345678")).containsExactly("휴대전화");
        assertThat(checker.find("+82 10-1234-5678")).containsExactly("휴대전화");
        assertThat(checker.find("(02) 123-4567")).containsExactly("유선전화");
    }

    @Test
    void normalizesFullWidthCharacters() {
        assertThat(checker.find("휴대폰 ０１０－７７７７－８８８８")).containsExactly("휴대전화");
    }

    @Test
    void passesMaskedTextAndCareerNumbers() {
        assertThat(checker.find("""
                [이름] | [전화번호] | [이메일]
                (주)가상커머스 2021.03 ~ 2024.12
                일 100만 건 처리, p95 320ms → 190ms, 초당 3,000건
                """)).isEmpty();
    }
}
