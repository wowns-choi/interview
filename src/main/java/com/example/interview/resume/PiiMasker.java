package com.example.interview.resume;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.interview.ai.dto.PiiResult;
import com.example.interview.ai.dto.PiiResult.Type;

/**
 * 로컬 AI가 찾은 개인정보를 이력서 글에서 가린다. 같은 값은 모두 가리고, 원문에 없는 값은 무시한다.
 */
@Component
public class PiiMasker {

    private static final Map<Type, String> LABELS = Map.ofEntries(
            Map.entry(Type.APPLICANT_NAME, "[이름]"),
            Map.entry(Type.OTHER_NAME, "[이름]"),
            Map.entry(Type.PHONE, "[전화번호]"),
            Map.entry(Type.EMAIL, "[이메일]"),
            Map.entry(Type.RRN, "[주민번호]"),
            Map.entry(Type.URL, "[링크]"),
            Map.entry(Type.BIRTH, "[생년월일]"),
            Map.entry(Type.ADDRESS, "[주소]"),
            Map.entry(Type.SENSITIVE, "[개인정보]"),
            Map.entry(Type.SCHOOL, "[학교]"),
            Map.entry(Type.COMPANY, "[회사]"),
            Map.entry(Type.SALARY, "[연봉]"),
            Map.entry(Type.CAREER, "[경력]"),
            Map.entry(Type.EDUCATION, "[학력]"),
            Map.entry(Type.TRAINING, "[교육]"),
            Map.entry(Type.CERTIFICATE, "[자격증]"));

    private static final int MAX_NAME = 50;

    public static String label(Type type) {
        return LABELS.get(type);
    }

    public record Masked(String text, Optional<String> applicantName) {
    }

    public Masked mask(String text, List<PiiResult.Item> items) {
        Optional<String> applicantName = items.stream()
                .filter(i -> i.getType() == Type.APPLICANT_NAME)
                .map(i -> i.getText().strip())
                .filter(name -> pattern(name).matcher(text).find())
                .findFirst()
                .map(name -> name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name);

        String result = text;
        // 긴 값부터 가려야 짧은 값이 긴 값의 일부만 가리지 않는다
        List<PiiResult.Item> sorted = items.stream()
                .filter(i -> i.getText() != null && !i.getText().isBlank())
                .sorted(Comparator.comparingInt((PiiResult.Item i) -> compact(i.getText()).length()).reversed())
                .toList();
        for (PiiResult.Item item : sorted) {
            result = pattern(item.getText()).matcher(result).replaceAll(Matcher.quoteReplacement(
                    item.getLabel() != null ? item.getLabel() : LABELS.get(item.getType())));
        }
        return new Masked(result, applicantName);
    }

    /**
     * 글자 사이 공백이 달라도 가린다 ("홍 길 동", "3,400 만원", "태화 이노베이션").
     * 로컬 AI가 띄어쓰기를 바꿔 옮겨 적어도 원문을 찾기 위해서다. 영문 대소문자도 구분하지 않는다.
     * 한 글자 값("남")은 다른 단어 속 글자까지 지우지 않도록 따로 떨어져 있을 때만 가린다.
     */
    private static Pattern pattern(String value) {
        String compact = compact(value);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < compact.length(); i++) {
            if (i > 0) {
                regex.append("\\s*");
            }
            regex.append(Pattern.quote(String.valueOf(compact.charAt(i))));
        }
        if (compact.length() == 1) {
            return Pattern.compile("(?<![\\p{L}\\p{N}])" + regex + "(?![\\p{L}\\p{N}])");
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String compact(String s) {
        return s.replaceAll("\\s+", "");
    }
}
