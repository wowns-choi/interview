package com.example.interview.resume;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 이력서 텍스트에서 지원자 이름을 찾는다. 이름 마스킹과 화면 표시용이다.
 * <ol>
 * <li>"성명: 홍길동", "이름 홍길동", "Name: 홍길동" 같은 표시</li>
 * <li>앞부분 줄 중 한글 2~4자(글자 사이 공백 허용)만 있는 줄</li>
 * </ol>
 * 못 찾으면 비어 있는 값을 돌려준다. 정규식 마스킹이므로 결과 화면에서 마스킹된 이력서를 확인할 수 있게 둔다.
 */
@Component
public class ResumeNameDetector {

    private static final Pattern LABELED = Pattern.compile(
            "(?:성\\s*명|이\\s*름|Name)\\s*[:：]?\\s*([가-힣](?:\\s?[가-힣]){1,3})(?![가-힣])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_ONLY_LINE = Pattern.compile("^([가-힣](?:\\s?[가-힣]){1,3})$");
    private static final int HEAD_LINES = 8;

    /** 이름처럼 보이지만 제목·항목명인 단어 */
    private static final Set<String> NOT_NAMES = Set.of(
            "이력서", "경력", "경력서", "학력", "자격증", "기술", "보유기술", "프로젝트", "자기소개", "자기소개서",
            "인적사항", "기본정보", "연락처", "주소", "교육", "수상", "경험", "요약", "경력요약", "포트폴리오",
            "병역", "어학", "활동", "대외활동", "지원분야", "희망직무", "입사지원서", "경력기술서");

    public Optional<String> detect(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher m = LABELED.matcher(text);
        while (m.find()) {
            String name = compact(m.group(1));
            if (!NOT_NAMES.contains(name)) {
                return Optional.of(name);
            }
        }
        int seen = 0;
        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (++seen > HEAD_LINES) {
                break;
            }
            Matcher lm = NAME_ONLY_LINE.matcher(trimmed);
            if (lm.matches()) {
                String name = compact(lm.group(1));
                if (!NOT_NAMES.contains(name)) {
                    return Optional.of(name);
                }
            }
        }
        return Optional.empty();
    }

    private static String compact(String s) {
        return s.replaceAll("\\s+", "");
    }
}
