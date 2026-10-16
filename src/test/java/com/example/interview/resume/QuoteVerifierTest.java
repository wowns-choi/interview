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
}
