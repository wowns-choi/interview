package com.example.interview.resume;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.interview.ai.dto.PiiResult.Type;

/**
 * 가린 뒤에도 주민번호·전화번호·이메일 모양이 남았는지 검사한다. 외부 AI로 보내기 전 마지막 확인이다.
 * 가리기는 로컬 AI가 하고, 이 클래스는 모양이 확실한 것만 마지막으로 가리고(maskObvious) 남았는지 찾는다(find).
 */
@Component
public class PiiLeakChecker {

    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        // 6자리-7자리 (뒷자리 일부가 * 로 가려진 경우 포함)
        PATTERNS.put("주민번호", Pattern.compile(
                "(?<!\\d)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\s*-\\s*[1-8][\\d*]{6}(?![\\d*])"));
        PATTERNS.put("이메일", Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
        // 010-0000-0000, 010 0000 0000, 01000000000, +82 10-...
        PATTERNS.put("휴대전화", Pattern.compile(
                "(?<!\\d)(?:\\+82[-.\\s]?1[016789]|01[016789])[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)"));
        // 02-000-0000, (031) 0000-0000
        PATTERNS.put("유선전화", Pattern.compile(
                "(?<!\\d)\\(?0(?:2|[3-6][1-5])\\)?[-.\\s]?\\d{3,4}[-.\\s]\\d{4}(?!\\d)"));
    }

    private static final Map<String, String> LABELS = Map.of(
            "주민번호", PiiMasker.label(Type.RRN), "이메일", PiiMasker.label(Type.EMAIL),
            "휴대전화", PiiMasker.label(Type.PHONE), "유선전화", PiiMasker.label(Type.PHONE));

    /**
     * 모양만으로 확실한 주민번호·이메일·전화번호를 가린다. 로컬 AI가 비상연락처 같은 두 번째 번호를 가끔 놓쳐서,
     * 업로드를 막기 전에 한 번 가린다. (전각 문자는 ResumeAnonymizer.normalize가 이미 바꿔 둔 글이어야 한다)
     */
    public String maskObvious(String text) {
        String out = text;
        for (Map.Entry<String, Pattern> e : PATTERNS.entrySet()) {
            out = e.getValue().matcher(out).replaceAll(java.util.regex.Matcher.quoteReplacement(LABELS.get(e.getKey())));
        }
        return out;
    }

    /** 남아 있는 항목 종류. 값은 돌려주지 않는다 (로그·화면에 개인정보를 남기지 않기 위해). */
    public List<String> find(String maskedText) {
        // 전각 숫자·기호(０１０－)도 일반 문자로 바꿔서 본다
        String normalized = Normalizer.normalize(maskedText, Normalizer.Form.NFKC);
        List<String> found = new ArrayList<>();
        PATTERNS.forEach((kind, pattern) -> {
            if (pattern.matcher(normalized).find()) {
                found.add(kind);
            }
        });
        return found;
    }
}
