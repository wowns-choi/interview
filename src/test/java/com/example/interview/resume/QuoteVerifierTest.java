package com.example.interview.resume;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuoteVerifierTest {

    private final QuoteVerifier verifier = new QuoteVerifier();
    private final String resume = "- 일 100만 건 주문 데이터\n  배치 처리 개선:   Spring Batch 적용\n- Redis 캐시 도입";

    @Test
    void exactQuoteIsVerified() {
        assertThat(verifier.verify("Redis 캐시 도입", resume)).isTrue();
    }

    @Test
    void whitespaceAndLineBreakDifferencesAreIgnored() {
        assertThat(verifier.verify("  일 100만 건 주문 데이터 배치 처리 개선: Spring Batch 적용 ", resume)).isTrue();
    }

    @Test
    void leadingBulletAndLookalikeDotsAreIgnored() {
        String text = "∙ Flutter 기반 앱 프론트엔드 구현 및 Spring Boot + MyBatis + MySQL 기반 백엔드\nAPI/DB 설계∙구현 (3인 개발)";
        assertThat(verifier.verify("· Spring Boot + MyBatis + MySQL 기반 백엔드 API/DB 설계·구현 (3인 개발)", text)).isTrue();
        assertThat(verifier.verify("• Redis 캐시 도입", resume)).isTrue();
        assertThat(verifier.verify("· Kafka 도입", text)).isFalse();
    }

    @Test
    void paraphrasedQuoteFails() {
        assertThat(verifier.verify("하루 100만 건 주문 배치 처리 개선", resume)).isFalse();
        assertThat(verifier.verify("Redis 캐시를 도입", resume)).isFalse();
    }

    @Test
    void emptyOrNullFails() {
        assertThat(verifier.verify(null, resume)).isFalse();
        assertThat(verifier.verify("   ", resume)).isFalse();
        assertThat(verifier.verify("Redis", null)).isFalse();
    }

    @Test
    void ignoresAllWhitespace() {
        String text = "DB 저장과 카프카에 이\n벤트 발행하는 것을 원자적으로";
        assertThat(new QuoteVerifier().verify("카프카에 이벤트 발행하는 것을", text)).isTrue();
        assertThat(new QuoteVerifier().verify("카프카에 이 벤트 발행", "카프카에이벤트발행")).isTrue();
    }
}
