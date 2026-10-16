package com.example.interview.resume;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** AI가 인용한 문장이 마스킹된 이력서 원문에 실제로 있는지 확인한다. */
@Component
public class QuoteVerifier {

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0\\u2007\\u202F\\u3000]+");

    public boolean verify(String quote, String maskedText) {
        if (quote == null || maskedText == null) {
            return false;
        }
        String q = normalize(quote);
        if (q.isEmpty()) {
            return false;
        }
        return normalize(maskedText).contains(q);
    }

    /** 공백·줄바꿈을 한 칸으로 합치고 앞뒤 공백을 제거한다. */
    static String normalize(String s) {
        return WHITESPACE.matcher(s).replaceAll(" ").strip();
    }
}
