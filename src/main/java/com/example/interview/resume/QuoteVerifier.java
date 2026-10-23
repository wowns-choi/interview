package com.example.interview.resume;

import org.springframework.stereotype.Component;

/** AI가 인용한 문장이 마스킹된 이력서 원문에 실제로 있는지 확인한다. */
@Component
public class QuoteVerifier {

    /** 가장 비슷한 원문은 이만큼 이상 비슷할 때만 보여 준다 (그보다 다르면 아무 문장이나 끌어온 셈) */
    static final double MIN_SIMILARITY = 0.5;
    /** 너무 긴 인용은 앞부분만으로 찾는다 (계산량: 인용 글자 수 × 원문 글자 수) */
    private static final int MAX_QUOTE_CHARS = 600;

    public boolean verify(String quote, String maskedText) {
        if (quote == null || maskedText == null) {
            return false;
        }
        String q = normalize(quote);
        return !q.isEmpty() && normalize(maskedText).contains(q);
    }

    /**
     * 글자와 숫자만 남긴다. PDF에서 뽑은 글은 줄바꿈·띄어쓰기·글머리표·쉼표가 AI가 옮겨 쓴 것과 자주 달라서
     * ("이 벤트" / "이벤트", "∙" / "·") 공백과 문장부호는 보지 않는다.
     */
    static String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints().filter(QuoteVerifier::counts).forEach(sb::appendCodePoint);
        return sb.toString();
    }

    private static boolean counts(int cp) {
        return Character.isLetterOrDigit(cp);
    }

    /**
     * 원문에서 인용과 가장 비슷한 구간을 원문 모양 그대로(줄바꿈·문장부호 포함) 돌려준다.
     * 글자와 숫자만 놓고 편집 거리가 가장 작은 부분 문자열을 찾는다. 비슷한 정도가 {@link #MIN_SIMILARITY} 미만이면 null.
     */
    public String closest(String quote, String text) {
        if (quote == null || text == null) {
            return null;
        }
        String q = normalize(quote);
        if (q.isEmpty()) {
            return null;
        }
        if (q.length() > MAX_QUOTE_CHARS) {
            q = q.substring(0, MAX_QUOTE_CHARS);
        }
        // 정리한 글자 → 원문 위치
        StringBuilder t = new StringBuilder();
        int[] pos = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (counts(ch)) {
                pos[t.length()] = i;
                t.append(ch);
            }
        }
        int m = q.length();
        int n = t.length();
        if (n == 0) {
            return null;
        }
        // 부분 문자열 정렬: 원문 어디서 시작해도 비용 0. start[j] = 그 칸까지 온 경로의 시작 위치
        int[] prev = new int[n + 1];
        int[] prevStart = new int[n + 1];
        int[] cur = new int[n + 1];
        int[] curStart = new int[n + 1];
        for (int j = 0; j <= n; j++) {
            prevStart[j] = j;
        }
        for (int i = 1; i <= m; i++) {
            cur[0] = i;
            curStart[0] = 0;
            char qc = q.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int sub = prev[j - 1] + (Character.toLowerCase(qc) == Character.toLowerCase(t.charAt(j - 1)) ? 0 : 1);
                int del = prev[j] + 1;     // 인용 글자가 원문에 없음
                int ins = cur[j - 1] + 1;  // 원문 글자가 인용에 없음
                if (sub <= del && sub <= ins) {
                    cur[j] = sub;
                    curStart[j] = prevStart[j - 1];
                } else if (del <= ins) {
                    cur[j] = del;
                    curStart[j] = prevStart[j];
                } else {
                    cur[j] = ins;
                    curStart[j] = curStart[j - 1];
                }
            }
            int[] tmp = prev; prev = cur; cur = tmp;
            tmp = prevStart; prevStart = curStart; curStart = tmp;
        }
        int best = 1;
        for (int j = 2; j <= n; j++) {
            if (prev[j] < prev[best]) {
                best = j;
            }
        }
        double similarity = 1.0 - (double) prev[best] / m;
        int start = prevStart[best];
        if (similarity < MIN_SIMILARITY || start >= best) {
            return null;
        }
        return text.substring(pos[start], pos[best - 1] + Character.charCount(text.codePointAt(pos[best - 1]))).strip();
    }
}
