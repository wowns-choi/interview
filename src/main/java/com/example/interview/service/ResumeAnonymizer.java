package com.example.interview.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.interview.ai.AiCallException;
import com.example.interview.ai.AiClient;
import com.example.interview.ai.AiInputTooLongException;
import com.example.interview.ai.PromptLoader;
import com.example.interview.ai.dto.PiiExtract;
import com.example.interview.ai.dto.PiiResult;
import com.example.interview.ai.dto.PiiResult.Type;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.CandidateProfile;
import com.example.interview.resume.PiiLeakChecker;
import com.example.interview.resume.PiiMasker;
import com.example.interview.resume.ResumeProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이력서 원문 → 로컬 AI가 이름·연락처·경력·학력·교육·자격증·연봉 등을 찾아 옮겨 적기 → 찾은 값을 모두 가리기 → 남은 개인정보 검사.
 * 긴 글은 줄 단위 조각으로 나눠 찾고, 찾은 값은 모아서 전체 글에서 가린다 (앞 조각에서 찾은 이름이 뒤에 나와도 가려진다).
 * 화면용 프로필에는 원문에 그대로 있는 값만 남긴다 (연봉은 가리기만 하고 남기지 않는다).
 * 경력은 회사·재직 기간·직급·연봉만 가리고 업무 내용은 남긴다. 외부 AI가 근거로 쓰기 때문이다.
 * 어느 단계든 실패하면 예외를 던져, 가리지 못한 글이 저장되거나 외부 AI로 나가지 않게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAnonymizer {

    /** 조각 하나의 최대 글자 수. 한국어는 약 1.8자가 1토큰이라 num_ctx 8192 안에 지시문·출력과 함께 들어간다 */
    static final int CHUNK_CHARS = 6_000;
    /** 이보다 짧은 조각이 한도를 넘으면 더 나누지 않고 실패로 본다 */
    private static final int MIN_SPLIT_CHARS = 200;
    private static final int MAX_FIELD = 200;
    private static final Pattern LEGAL_FORM = Pattern.compile(
            "\\(\\s*[주유]\\s*\\)|㈜|주식회사|유한회사|,?\\s*\\b(?:Co\\.,?\\s*Ltd\\.?|Inc\\.?|Corp\\.?)", Pattern.CASE_INSENSITIVE);

    /**
     * 모양이 정해져 있어 로컬 AI가 놓쳐도 코드로 가리는 값. 첫 번째 그룹이 가릴 부분이다.
     * 나이("33세", "만 33세"; "20세기", "5세대"는 제외), 주소 앞 우편번호, "생년월일" 뒤의 날짜, "주소" 뒤의 시·도로 시작하는 줄 나머지, 연봉 이름표 뒤의 금액, 학점,
     * 학교 종류("대학교(4년)", "(2·3년제)"), 인정학력 줄, "장애 : 비대상"·"보훈 : 비대상"·"병역 : 군필" 같은 칸의 값.
     */
    private static final Map<Pattern, Type> LABELED = Map.ofEntries(
            Map.entry(Pattern.compile("(?<![\\p{N}.])((?:만\\s*)?\\d{2}\\s*세)(?![기대트계])"), Type.BIRTH),
            Map.entry(Pattern.compile("주소[^\\n\\d]{0,5}?(\\(?\\d{5}\\)?)"), Type.ADDRESS),
            Map.entry(Pattern.compile("생년월일\\s*[:：]?\\s*(\\d{2,4}\\s*[./년-]\\s*\\d{1,2}\\s*[./월-]\\s*\\d{1,2}\\s*일?)"), Type.BIRTH),
            Map.entry(Pattern.compile("주소\\s*[:：]?\\s*(?:\\(?\\d{5}\\)?\\s*)?((?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충청|충북|충남|전라|전북|전남|경상|경북|경남|제주)[^\\n]*)"), Type.ADDRESS),
            Map.entry(Pattern.compile("(?:연봉|급여|월급)[^\\n\\d]{0,8}?(\\d[\\d,.]*\\s*(?:만\\s*원|천\\s*만\\s*원|원|만))"), Type.SALARY),
            Map.entry(Pattern.compile("학점\\s*[:：]?\\s*(\\d(?:\\.\\d+)?\\s*/\\s*\\d(?:\\.\\d+)?)"), Type.EDUCATION),
            Map.entry(Pattern.compile("대학(?:교|원)?\\s*(\\(\\s*\\d(?:\\s*·\\s*\\d)?\\s*년제?\\s*\\))"), Type.EDUCATION),
            Map.entry(Pattern.compile("(\\(\\s*\\d(?:\\s*·\\s*\\d)?\\s*년제\\s*\\))"), Type.EDUCATION),
            Map.entry(Pattern.compile("인정학력\\s*[:：]?\\s*([^\\n]+)"), Type.EDUCATION),
            Map.entry(Pattern.compile("(학점\\s*은행제|검정\\s*고시|독학사)"), Type.EDUCATION),
            Map.entry(Pattern.compile("(?m)(?:장애|보훈|병역)(?:\\s*[:：]|\\s*(?:여부|사항|대상|구분)\\s*[:：]?)\\s*([^\\n:：]{1,20}?)(?=\\s*(?:장애|보훈|병역)|\\s*$)"), Type.SENSITIVE),
            // 병역 표: 이름표 없이 띄어 쓴 값도 가린다. 병역 상태는 어디서든, 계급·군별·면제 사유는 이름표 뒤에서만
            Map.entry(Pattern.compile("(병역\\s*필|군\\s*필|미\\s*필|만기\\s*(?:제대|전역)|의가사\\s*제대|의병\\s*제대|병역\\s*면제|소집\\s*해제"
                    + "|의무\\s*경찰|의무\\s*소방|사회\\s*복무\\s*요원|산업\\s*기능\\s*요원|전문\\s*연구\\s*요원|공익\\s*근무\\s*요원)"), Type.SENSITIVE),
            Map.entry(Pattern.compile("계급\\s*[:：]?\\s*(이병|일병|상병|병장|이경|일경|상경|수경|하사|중사|상사|원사|준위|소위|중위|대위|소령|중령|대령)"), Type.SENSITIVE),
            Map.entry(Pattern.compile("((?:이경|일경|상경|수경)?\\s*\\(\\s*(?:이병|일병|상병|병장|이경|일경|상경|수경)\\s*\\))"), Type.SENSITIVE),
            Map.entry(Pattern.compile("(?:군별|역종|병과|주특기|군번|(?:제대|전역|복무)\\s*구분)(?:\\s*[:：]\\s*|\\s+)([^\\s:：\\[]+)"), Type.SENSITIVE),
            Map.entry(Pattern.compile("(?m)면제[ \\t]*사유[ \\t]*[:：]?[ \\t]*([^\\s\\[][^\\n]{0,29})$"), Type.SENSITIVE),
            Map.entry(Pattern.compile("(면접\\s*후\\s*결정|회사\\s*내규\\s*에?\\s*따름|추후\\s*협의|협의\\s*후\\s*결정)"), Type.SALARY));

    /** PDF에서 나온 특수 공백(줄바꿈 없는 공백 등)과 전각 문자를 보통 글자로 바꾼다. 그래야 띄어쓰기를 무시한 비교가 맞는다 */
    private static final Pattern ODD_SPACE = Pattern.compile("[\\p{Zs}\\t\\x0B\\f]");
    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200D\\uFEFF\\u00AD]");

    static String normalize(String text) {
        String s = INVISIBLE.matcher(text.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("");
        s = ODD_SPACE.matcher(s).replaceAll(" ");
        // 전각 영숫자·기호("３，４００")만 반각으로. 한글 자모("ㆍ")까지 바꾸는 NFKC는 쓰지 않는다
        StringBuilder sb = new StringBuilder(s.length());
        s.chars().forEach(c -> sb.append((char) (c >= 0xFF01 && c <= 0xFF5E ? c - 0xFEE0 : c)));
        return sb.toString();
    }

    private final AiClient aiClient;
    private final PromptLoader promptLoader;
    private final PiiMasker piiMasker;
    private final PiiLeakChecker leakChecker;

    public record Result(String text, Optional<String> applicantName, CandidateProfile profile) {
    }

    /** @param positionId ai_call_log의 ref_id (지원자는 아직 없다) */
    public Result anonymize(Long positionId, String original) {
        String text = normalize(original);

        // 1. 로컬 AI 응답을 받아 합친다. 화면용 프로필에는 원문에 있는 값만 남긴다
        List<PiiExtract> found = detectAll(positionId, text);
        CandidateProfile profile = verify(merge(found.stream().map(PiiExtract::toProfile).toList()), text);

        // 2. 같은 회사는 한 줄로 합치고 별칭을 붙인다 (A사, B사 / 학교1 / 교육1 / 활동1)
        profile.setCareers(byCompany(profile.getCareers()));
        Map<String, String> companyLabels = aliasCompanies(profile.getCareers(),
                found.stream().flatMap(f -> f.companyNames().stream()).toList());
        aliasOthers(profile);

        // 3. 가릴 목록: 찾은 값 전부. 회사는 별칭으로 가린다
        List<PiiResult.Item> items = maskList(text, found, companyLabels);

        // 4. 가리기
        PiiMasker.Masked masked = piiMasker.mask(text, items);
        String maskedText = leakChecker.maskObvious(maskLabeled(masked.text()));
        List<String> leaks = leakChecker.find(maskedText);
        if (!leaks.isEmpty()) {
            log.warn("가린 뒤에도 개인정보가 남음 positionId={}: {}", positionId, leaks);
            throw new ResumeProcessingException("가리지 못한 개인정보(" + String.join(", ", leaks)
                    + ")가 남아 있어 이력서를 저장하지 않았습니다.");
        }

        // 5. 저장은 호출한 쪽이 한다 (가린 글, 별칭이 붙은 프로필, 지원자 이름)
        return new Result(maskedText, masked.applicantName(), profile);
    }

    private List<PiiExtract> detectAll(Long positionId, String text) {
        String prompt = promptLoader.load("pii_detect");
        List<PiiExtract> found = new ArrayList<>();
        try {
            for (String chunk : split(text, CHUNK_CHARS)) {
                detect(positionId, prompt, chunk, found);
            }
        } catch (AiCallException e) {
            log.warn("개인정보 탐지 실패 positionId={}: {}", positionId, e.getMessage());
            throw new ResumeProcessingException("개인정보를 가리지 못해 이력서를 저장하지 않았습니다. 로컬 AI(Ollama)가 실행 중인지 확인하세요.", e);
        }
        return found;
    }

    /**
     * 찾은 값 전부에 이름표를 정한다. 회사는 법인 표기를 뗀 이름도 넣고, 별칭이 있으면 "[A사]"로 가린다.
     * 나이·우편번호·연봉처럼 모양이 정해진 값은 로컬 AI가 놓쳐도 같은 값이 다른 곳에 나오면 가리도록 함께 넣는다
     * (학력 표기와 장애·보훈·병역 값은 업무 내용에도 나올 수 있어 maskLabeled가 찾은 자리에서만 가린다).
     */
    static List<PiiResult.Item> maskList(String text, List<PiiExtract> found, Map<String, String> companyLabels) {
        List<PiiResult.Item> items = new ArrayList<>();
        for (PiiExtract f : found) {
            for (PiiResult.Item item : f.toItems()) {
                if (item.getType() != Type.COMPANY) {
                    items.add(item);
                    continue;
                }
                for (String name : companyNames(item.getText())) {
                    PiiResult.Item company = new PiiResult.Item(Type.COMPANY, name);
                    company.setLabel(companyLabels.get(companyKey(name)));
                    items.add(company);
                }
            }
        }
        LABELED.forEach((pattern, type) -> {
            if (type != Type.EDUCATION && type != Type.SENSITIVE) {
                pattern.matcher(text).results().forEach(m -> add(items, type, m.group(1)));
            }
        });
        return items;
    }

    /** 모양이 정해진 값을 찾은 자리에서 가린다 (이름표는 남긴다). */
    static String maskLabeled(String text) {
        String result = text;
        for (Map.Entry<Pattern, Type> e : LABELED.entrySet()) {
            result = e.getKey().matcher(result).replaceAll(m -> Matcher.quoteReplacement(
                    m.group().substring(0, m.start(1) - m.start()) + PiiMasker.label(e.getValue())
                            + m.group().substring(m.end(1) - m.start())));
        }
        return result;
    }

    /** 입력이 잘리면 반으로 나눠 다시 부른다. */
    private void detect(Long positionId, String prompt, String chunk, List<PiiExtract> found) {
        try {
            found.add(aiClient.callLocal(AiPurpose.PII_DETECT, positionId, prompt,
                    "<resume>\n" + chunk + "\n</resume>", PiiExtract.SCHEMA, PiiExtract.class, PiiExtract::validate));
        } catch (AiInputTooLongException e) {
            if (chunk.length() < MIN_SPLIT_CHARS) {
                throw e;
            }
            for (String half : split(chunk, (chunk.length() + 1) / 2)) {
                detect(positionId, prompt, half, found);
            }
        }
    }

    /** 조각별 결과를 합친다. 목록은 이어 붙이고, 한 값짜리는 처음 나온 값을 쓴다. 중복은 verify에서 뺀다. */
    static CandidateProfile merge(List<CandidateProfile> profiles) {
        CandidateProfile m = new CandidateProfile();
        for (CandidateProfile p : profiles) {
            m.setGender(first(m.getGender(), p.getGender()));
            m.setBirth(first(m.getBirth(), p.getBirth()));
            m.setPhone(first(m.getPhone(), p.getPhone()));
            m.setEmail(first(m.getEmail(), p.getEmail()));
            m.setAddress(first(m.getAddress(), p.getAddress()));
            m.setLink(first(m.getLink(), p.getLink()));
            m.setTotalCareer(first(m.getTotalCareer(), p.getTotalCareer()));
            m.setLastSalary(first(m.getLastSalary(), p.getLastSalary()));
            m.setDesiredSalary(first(m.getDesiredSalary(), p.getDesiredSalary()));
            addAll(m.getEducation(), p.getEducation());
            addAll(m.getCareers(), p.getCareers());
            addAll(m.getTrainings(), p.getTrainings());
            addAll(m.getCertificates(), p.getCertificates());
            addAll(m.getActivities(), p.getActivities());
        }
        return m;
    }

    /** 로컬 AI가 지어낸 값이 화면에 나오지 않도록, 원문에 그대로 있는 값만 남긴다. */
    static CandidateProfile verify(CandidateProfile p, String text) {
        String source = compact(text);
        UnaryOperator<String> keep = v -> {
            if (v == null || v.isBlank()) {
                return "";
            }
            String value = v.strip();
            return value.length() <= MAX_FIELD && source.contains(compact(value)) ? value : "";
        };
        CandidateProfile out = new CandidateProfile();
        out.setGender(keep.apply(p.getGender()));
        out.setBirth(keep.apply(p.getBirth()));
        out.setPhone(keep.apply(p.getPhone()));
        out.setEmail(keep.apply(p.getEmail()));
        out.setAddress(keep.apply(p.getAddress()));
        out.setLink(keep.apply(p.getLink()));
        out.setTotalCareer(keep.apply(p.getTotalCareer()));
        out.setLastSalary(keep.apply(p.getLastSalary()));
        out.setDesiredSalary(keep.apply(p.getDesiredSalary()));
        out.setEducation(clean(p.getEducation(), e -> {
            CandidateProfile.Education v = new CandidateProfile.Education();
            v.setSchool(keep.apply(e.getSchool()));
            v.setMajor(keep.apply(e.getMajor()));
            v.setStatus(keep.apply(e.getStatus()));
            v.setPeriod(keep.apply(e.getPeriod()));
            v.setGpa(keep.apply(e.getGpa()));
            return v;
        }, CandidateProfile.Education::isBlank));
        out.setCareers(clean(p.getCareers(), c -> {
            CandidateProfile.Career v = new CandidateProfile.Career();
            v.setCompany(keep.apply(c.getCompany()));
            v.setDepartment(keep.apply(c.getDepartment()));
            v.setPosition(keep.apply(c.getPosition()));
            v.setPeriod(keep.apply(c.getPeriod()));
            v.setDuration(keep.apply(c.getDuration()));
            v.setSalary(keep.apply(c.getSalary()));
            return v;
        }, CandidateProfile.Career::isBlank));
        out.setTrainings(clean(p.getTrainings(), t -> {
            CandidateProfile.Training v = new CandidateProfile.Training();
            v.setName(keep.apply(t.getName()));
            v.setInstitution(keep.apply(t.getInstitution()));
            v.setPeriod(keep.apply(t.getPeriod()));
            return v;
        }, CandidateProfile.Training::isBlank));
        out.setCertificates(clean(p.getCertificates(), c -> {
            CandidateProfile.Certificate v = new CandidateProfile.Certificate();
            v.setName(keep.apply(c.getName()));
            v.setIssuer(keep.apply(c.getIssuer()));
            v.setDate(keep.apply(c.getDate()));
            return v;
        }, CandidateProfile.Certificate::isBlank));
        out.setActivities(clean(p.getActivities(), a -> {
            CandidateProfile.Activity v = new CandidateProfile.Activity();
            // 종류는 로컬 AI가 붙인 분류라 원문 확인 없이 짧게만 받는다
            String kind = a.getKind() == null ? "" : a.getKind().strip();
            v.setKind(kind.length() > 20 ? kind.substring(0, 20) : kind);
            v.setName(keep.apply(a.getName()));
            v.setPeriod(keep.apply(a.getPeriod()));
            return v;
        }, CandidateProfile.Activity::isBlank));
        return out;
    }

    /** 각 항목을 검증한 값으로 바꾸고, 빈 항목과 중복을 뺀다. */
    private static <T> List<T> clean(List<T> list, Function<T, T> verify, Predicate<T> blank) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        if (list != null) {
            list.stream().filter(e -> e != null).map(verify).filter(blank.negate()).forEach(out::add);
        }
        return new ArrayList<>(out);
    }

    private static <T> void addAll(List<T> target, List<T> source) {
        if (source != null) {
            target.addAll(source);
        }
    }

    private static void add(List<PiiResult.Item> items, Type type, String value) {
        if (value != null && !value.isBlank()) {
            items.add(new PiiResult.Item(type, value));
        }
    }

    /** "가온소프트(주)"를 가리면 본문의 "가온소프트"도 가려지도록, 법인 표기를 뗀 이름도 함께 낸다. */
    static List<String> companyNames(String company) {
        if (company == null || company.isBlank()) {
            return List.of();
        }
        String core = LEGAL_FORM.matcher(company).replaceAll("").strip();
        return core.length() >= 2 && !core.equals(company.strip()) ? List.of(company, core) : List.of(company);
    }

    /**
     * 같은 회사를 한 줄로 합친다. 경력 요약표와 경력기술서(프로젝트마다 회사명·근무기간 반복)에 같은 회사가 여러 번 나오기 때문이다.
     * 칸마다 먼저 나온 값을 쓴다. 보통 요약표가 앞에 있어 실제 재직 기간이 남는다.
     */
    static List<CandidateProfile.Career> byCompany(List<CandidateProfile.Career> careers) {
        Map<String, CandidateProfile.Career> merged = new LinkedHashMap<>();
        for (CandidateProfile.Career c : careers) {
            String key = companyKey(c.getCompany());
            if (key.isEmpty()) {
                key = "#" + merged.size();
            }
            CandidateProfile.Career m = merged.get(key);
            if (m == null) {
                merged.put(key, c);
                continue;
            }
            m.setDepartment(first(m.getDepartment(), c.getDepartment()));
            m.setPosition(first(m.getPosition(), c.getPosition()));
            m.setPeriod(first(m.getPeriod(), c.getPeriod()));
            m.setDuration(first(m.getDuration(), c.getDuration()));
            m.setSalary(first(m.getSalary(), c.getSalary()));
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 경력 목록 순서로 회사마다 "A사", "B사" 별칭을 정해 프로필에 적고, 회사 이름 → "[A사]" 표를 돌려준다.
     * 이력서 글의 회사 이름은 "[회사]" 대신 이 이름표로 가린다. 외부 AI에 보내는 회사 목록도 같은 별칭을 써서, 본문의 업무가 어느 회사 일인지 알 수 있다.
     * 같은 회사의 다른 표기(names의 한 묶음)도 같은 이름표를 쓴다. 표에 없는 회사 이름은 "[회사]" 그대로다.
     */
    static Map<String, String> aliasCompanies(List<CandidateProfile.Career> careers, List<List<String>> names) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < careers.size(); i++) {
            CandidateProfile.Career c = careers.get(i);
            c.setAlias(CareerEvalService.alias(i));
            String key = companyKey(c.getCompany());
            if (!key.isEmpty()) {
                labels.putIfAbsent(key, "[" + c.getAlias() + "]");
            }
        }
        for (List<String> group : names) {
            group.stream().map(n -> labels.get(companyKey(n))).filter(l -> l != null).findFirst()
                    .ifPresent(label -> group.forEach(n -> labels.putIfAbsent(companyKey(n), label)));
        }
        return labels;
    }

    /** 학교·교육·활동에 목록 순서로 "학교1", "교육1", "활동1" 별칭을 적는다. 타임라인 호출과 화면이 이 별칭으로 항목을 찾는다 */
    static void aliasOthers(CandidateProfile profile) {
        for (int i = 0; i < profile.getEducation().size(); i++) {
            profile.getEducation().get(i).setAlias(CareerEvalService.SCHOOL + (i + 1));
        }
        for (int i = 0; i < profile.getTrainings().size(); i++) {
            profile.getTrainings().get(i).setAlias(CareerEvalService.TRAINING + (i + 1));
        }
        for (int i = 0; i < profile.getActivities().size(); i++) {
            profile.getActivities().get(i).setAlias(CareerEvalService.ACTIVITY + (i + 1));
        }
    }

    /** 법인 표기와 띄어쓰기를 뺀 소문자 이름. 같은 회사인지 비교할 때 쓴다 */
    private static String companyKey(String company) {
        List<String> names = companyNames(company);
        return names.isEmpty() ? "" : compact(names.get(names.size() - 1)).toLowerCase();
    }

    private static String first(String current, String next) {
        return current != null && !current.isBlank() ? current : next == null ? "" : next;
    }

    private static String compact(String s) {
        return s.replaceAll("\\s+", "");
    }

    /** 줄 단위로 max 글자 이하 조각으로 나눈다. 한 줄이 max보다 길면 그 줄만 글자 단위로 자른다. */
    static List<String> split(String text, int max) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("(?<=\n)")) {
            if (current.length() + line.length() > max && !current.isEmpty()) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            while (line.length() > max) {
                chunks.add(line.substring(0, max));
                line = line.substring(max);
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            // 마지막 조각이 아주 짧으면 따로 부르지 않고 앞 조각에 붙인다 (num_ctx 안에 넉넉히 들어가는 만큼만)
            int last = chunks.size() - 1;
            if (last >= 0 && current.length() < max / 4 && chunks.get(last).length() + current.length() <= max + max / 4) {
                chunks.set(last, chunks.get(last) + current);
            } else {
                chunks.add(current.toString());
            }
        }
        return chunks;
    }
}
