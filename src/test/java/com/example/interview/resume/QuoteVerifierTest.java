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
    void punctuationDifferencesAreIgnored() {
        String text = "- URL 하나만 입력하면, 크롤링 -> AI 글 생성 -> 블로거 업로드 까지 자동화";
        assertThat(verifier.verify("URL 하나만 입력하면 크롤링, AI 글 생성, 블로거 업로드까지 자동화", text)).isTrue();
    }

    @Test
    void closestShowsTheOriginalSpanAsWritten() {
        String text = "[A사] 2023.01 ~ 2024.06\n∙ 대법원 경매 사이트를 Selenium으로 크롤링해 60개 법원, 수만 건 규모의 경매 데이터를\n"
                + "자동 수집하던 중 봇 감지로 IP가 차단되는 문제 발생\n- Redis 캐시 도입";
        String quote = "대법원 경매 사이트를 Selenium으로 크롤링해 60개 법원, 수만 건 규모의 경매 데이터를 자동 수집·처리하고";
        assertThat(verifier.verify(quote, text)).isFalse();
        assertThat(verifier.closest(quote, text))
                .isEqualTo("대법원 경매 사이트를 Selenium으로 크롤링해 60개 법원, 수만 건 규모의 경매 데이터를\n자동 수집하");
    }

    @Test
    void closestIsNullWhenNothingIsSimilar() {
        assertThat(verifier.closest("Kubernetes 클러스터 운영 경험", resume)).isNull();
        assertThat(verifier.closest(null, resume)).isNull();
    }

    @Test
    void ignoresAllWhitespace() {
        String text = "DB 저장과 카프카에 이\n벤트 발행하는 것을 원자적으로";
        assertThat(new QuoteVerifier().verify("카프카에 이벤트 발행하는 것을", text)).isTrue();
        assertThat(new QuoteVerifier().verify("카프카에 이 벤트 발행", "카프카에이벤트발행")).isTrue();
    }

    @Test
    void ellipsisJoinedQuotePassesWhenEveryPieceIsInOrder() {
        String text = "자기소개 저는 구조를 이해하는 걸 좋아했고, 현재 회사에서는 자사 서비스를 운영하며 직접 경험하고 있습니다.";
        assertThat(verifier.verify("자기소개...현재 회사에서는 자사 서비스를 운영하며", text)).isTrue();
        assertThat(verifier.verify("자기소개 … 직접 경험하고 있습니다.", text)).isTrue();
        // 조각 하나라도 없거나 순서가 다르면 못 찾은 것
        assertThat(verifier.verify("자기소개...Kafka 운영", text)).isFalse();
        assertThat(verifier.verify("현재 회사에서는...자기소개", text)).isFalse();
        assertThat(verifier.verify("...", text)).isFalse();
    }

    @Test
    void sameSpanReturnsOriginalWhenOnlyAFewLettersDiffer() {
        String text = "주요 업무\n- AWS 및 CloudV 환경에 서비스 배포, Cloudflare–Nginx–Spring–DB–Python 서버 간 전체 아키텍처 직접 설계 및 구현\n"
                + "- 사내 결제 모듈 리팩터링";
        String quote = "AWS 및 CloudV 환경에 서비스 배포, Cloudflare–Nginx–Spring–DB–Python 전체 아키텍처를 직접 설계 및 구현";
        assertThat(verifier.verify(quote, text)).isFalse();
        assertThat(verifier.sameSpan(quote, text))
                .isEqualTo("AWS 및 CloudV 환경에 서비스 배포, Cloudflare–Nginx–Spring–DB–Python 서버 간 전체 아키텍처 직접 설계 및 구현");
        assertThat(verifier.sameSpan("사내 결제 모듈을 처음부터 새로 개발", text)).isNull();
    }
}
