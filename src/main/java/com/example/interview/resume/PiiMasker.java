package com.example.interview.resume;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 이력서 텍스트의 개인정보를 정규식으로 가린다.
 * 정규식으로 다 잡히지 않을 수 있으므로, 분석 전에 사람이 마스킹 결과를 반드시 확인한다.
 */
@Component
public class PiiMasker {

    public static final String NAME = "[이름]";
    public static final String PHONE = "[전화번호]";
    public static final String EMAIL = "[이메일]";
    public static final String RRN = "[주민번호]";
    public static final String BIRTH = "[생년월일]";
    public static final String ADDRESS = "[주소]";

    /** 주민등록번호: 6자리-7자리 (뒷자리 일부가 * 로 가려진 경우 포함) */
    private static final Pattern RRN_PATTERN = Pattern.compile(
            "(?<!\\d)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\s*-\\s*[1-8][\\d*]{6}(?![\\d*])");

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** 휴대전화: 010-0000-0000, 010 0000 0000, 010.0000.0000, 01000000000, +82 10-... */
    private static final Pattern MOBILE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\+82[-.\\s]?1[016789]|01[016789])[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)");

    /** 지역번호 유선전화: 02-000-0000, 031.0000.0000, (02) 000-0000 */
    private static final Pattern LANDLINE_PATTERN = Pattern.compile(
            "(?<!\\d)\\(?0(?:2|[3-6][1-5])\\)?[-.\\s]?\\d{3,4}[-.\\s]\\d{4}(?!\\d)");

    // 두 자리를 먼저 시도해야 "14일"이 "1"에서 끊기지 않는다
    private static final String DATE = "(?:19|20)\\d{2}\\s*(?:년|[./-])\\s*(?:1[0-2]|0?[1-9])\\s*(?:월|[./-])\\s*(?:3[01]|[12]\\d|0?[1-9])(?!\\d)\\s*일?";

    /**
     * 생년월일. 프로젝트 기간 같은 일반 날짜까지 가리면 근거 분석에 지장이 있으므로
     * "생년월일/생일/출생/Birth" 표시 뒤의 날짜, 또는 "...일생" 형식만 가린다.
     */
    private static final Pattern BIRTH_LABELED_PATTERN = Pattern.compile(
            "((?:생년월일|생\\s*일|출\\s*생(?:일)?|Birth(?:day|\\s*date)?|D\\.?O\\.?B\\.?)\\s*[:：)\\]]?\\s*)" + DATE + "(?:\\s*생)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BIRTH_SUFFIX_PATTERN = Pattern.compile(DATE + "\\s*생(?![가-힣])");

    /** 도로명 주소: "시/군/구 (동/구)" 다음의 "○○로/길 + 번호 (+ 동·호)" 부분을 가린다. 지역 단위는 남긴다. */
    private static final Pattern ROAD_ADDRESS_PATTERN = Pattern.compile(
            "((?:[가-힣]+(?:특별시|광역시|특별자치시|특별자치도|도|시)\\s+)?[가-힣]+(?:시|군|구)\\s+(?:[가-힣]+(?:구|읍|면|동)\\s+)?)"
                    + "[가-힣0-9]+(?:로|길)\\s*\\d+(?:-\\d+)?(?:번?길\\s*\\d+(?:-\\d+)?)?"
                    + "(?:\\s*,?\\s*\\d+\\s*동)?(?:\\s*,?\\s*\\d+\\s*호)?(?:\\s*\\([^)]{1,30}\\))?");

    /** 지번 주소: "○○동 123-45번지" */
    private static final Pattern LOT_ADDRESS_PATTERN = Pattern.compile(
            "([가-힣]+(?:동|리|가)\\s*)\\d+(?:-\\d+)?\\s*번지(?:\\s*,?\\s*\\d+\\s*동)?(?:\\s*,?\\s*\\d+\\s*호)?");

    public String mask(String text, String candidateName) {
        if (text == null) {
            return null;
        }
        String result = text;
        // 숫자 패턴은 긴 것부터: 주민번호 → 전화번호 순
        result = RRN_PATTERN.matcher(result).replaceAll(Matcher.quoteReplacement(RRN));
        result = EMAIL_PATTERN.matcher(result).replaceAll(Matcher.quoteReplacement(EMAIL));
        result = BIRTH_LABELED_PATTERN.matcher(result).replaceAll("$1" + Matcher.quoteReplacement(BIRTH));
        result = BIRTH_SUFFIX_PATTERN.matcher(result).replaceAll(Matcher.quoteReplacement(BIRTH));
        result = MOBILE_PATTERN.matcher(result).replaceAll(Matcher.quoteReplacement(PHONE));
        result = LANDLINE_PATTERN.matcher(result).replaceAll(Matcher.quoteReplacement(PHONE));
        result = ROAD_ADDRESS_PATTERN.matcher(result).replaceAll("$1" + Matcher.quoteReplacement(ADDRESS));
        result = LOT_ADDRESS_PATTERN.matcher(result).replaceAll("$1" + Matcher.quoteReplacement(ADDRESS));
        result = maskName(result, candidateName);
        return result;
    }

    /** 이름은 글자 사이 공백이 있는 경우("홍 길 동")도 가린다. */
    private String maskName(String text, String name) {
        if (name == null || name.isBlank()) {
            return text;
        }
        String compact = name.replaceAll("\\s+", "");
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < compact.length(); i++) {
            if (i > 0) {
                regex.append("\\s*");
            }
            regex.append(Pattern.quote(String.valueOf(compact.charAt(i))));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(NAME));
    }
}
