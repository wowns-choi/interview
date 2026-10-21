package com.example.interview.resume;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** AI가 인용한 문장이 마스킹된 이력서 원문에 실제로 있는지 확인한다. */
@Component
public class QuoteVerifier {

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0\\u2007\\u202F\\u3000]+");
    /** 모양이 비슷한 가운뎃점 글머리표는 하나로 본다 (원문 ∙ 를 AI가 · 로 옮겨 쓴다) */
    private static final Pattern DOT_BULLET = Pattern.compile("[·∙•・‧⋅]");
    /** AI가 문장 중간부터 인용하면서 앞에 붙이는 글머리표 */
    private static final Pattern EDGE_BULLETS = Pattern.compile("^[\\s·▪◦●○■□◆◇▶►※*\\-–—]+|[\\s·▪◦●○■□◆◇▶►※*\\-–—]+$");

    public boolean verify(String quote, String maskedText) {
        if (quote == null || maskedText == null) {
            return false;
        }
        String q = EDGE_BULLETS.matcher(normalize(quote)).replaceAll("");
        if (q.isEmpty()) {
            return false;
        }
        return normalize(maskedText).contains(q);
    }

    /** 공백·줄바꿈을 한 칸으로 합치고 가운뎃점 글머리표를 통일한 뒤 앞뒤 공백을 제거한다. */
    static String normalize(String s) {
        return WHITESPACE.matcher(DOT_BULLET.matcher(s).replaceAll("·")).replaceAll(" ").strip();
    }
}
