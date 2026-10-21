package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.example.interview.ai.dto.PiiResult.Type;
import com.example.interview.domain.CandidateProfile;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * 로컬 AI가 이력서에서 정해진 정보를 원문 그대로 옮겨 적은 응답 (PII_DETECT).
 * 모델에게는 가린다는 목적을 알리지 않고 찾아 적게만 한다. 가릴 목록(toItems)과 화면용 프로필(toProfile)은 코드가 만든다.
 * 예전에 "가릴 값"과 "옮겨 적을 값"을 따로 시켰더니, 모델이 민감한 값을 프로필에서 빼야 하는 것으로 오해해 둘 다 비웠다.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PiiExtract {

    private static final Map<String, Object> STRING = Map.of("type", "string");
    private static final Map<String, Object> STRINGS = Map.of("type", "array", "items", STRING);

    /** 로컬 AI 출력 형식 (Ollama format). 순서대로 출력하게 한다 */
    public static final Map<String, Object> SCHEMA;

    static {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("이름", STRINGS);
        props.put("다른 사람 이름", STRINGS);
        props.put("성별", STRING);
        props.put("생년월일", STRINGS);
        props.put("연락처", STRINGS);
        props.put("이메일", STRINGS);
        props.put("주소", STRINGS);
        props.put("링크", STRINGS);
        props.put("학력", array("학교", STRING, "전공", STRING, "상태", STRING, "기간", STRING, "학점", STRING));
        props.put("경력", array("회사", STRINGS, "부서", STRING, "직급", STRING, "재직 기간", STRING, "근무 기간", STRING));
        props.put("총 경력", STRING);
        props.put("연봉", STRINGS);
        props.put("교육", array("과정", STRING, "기관", STRING, "기간", STRING));
        props.put("자격증", array("이름", STRING, "기관", STRING, "취득일", STRING));
        props.put("활동", array("종류", STRING, "이름", STRING, "기간", STRING));
        props.put("기타 개인정보", STRINGS);
        SCHEMA = Map.of("type", "object", "required", List.copyOf(props.keySet()), "properties", props);
    }

    @JsonProperty("이름")
    private List<String> names;
    @JsonProperty("다른 사람 이름")
    private List<String> otherNames;
    @JsonProperty("성별")
    private String gender;
    @JsonProperty("생년월일")
    private List<String> births;
    @JsonProperty("연락처")
    private List<String> phones;
    @JsonProperty("이메일")
    private List<String> emails;
    @JsonProperty("주소")
    private List<String> addresses;
    @JsonProperty("링크")
    private List<String> links;
    @JsonProperty("학력")
    private List<School> education;
    @JsonProperty("경력")
    private List<Job> careers;
    @JsonProperty("총 경력")
    private String totalCareer;
    @JsonProperty("연봉")
    private List<String> salaries;
    @JsonProperty("교육")
    private List<Course> trainings;
    @JsonProperty("자격증")
    private List<License> certificates;
    @JsonProperty("활동")
    private List<Activity> activities;
    @JsonProperty("기타 개인정보")
    private List<String> others;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class School {
        @JsonProperty("학교")
        private String school;
        @JsonProperty("전공")
        private String major;
        @JsonProperty("상태")
        private String status;
        @JsonProperty("기간")
        private String period;
        @JsonProperty("학점")
        private String gpa;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Job {
        /** 같은 회사의 여러 표기 ("(주)가나소프트", "가나소프트", "Gana Soft") */
        @JsonProperty("회사")
        private List<String> company;
        @JsonProperty("부서")
        private String department;
        @JsonProperty("직급")
        private String position;
        @JsonProperty("재직 기간")
        private String period;
        @JsonProperty("근무 기간")
        private String duration;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Course {
        @JsonProperty("과정")
        private String name;
        @JsonProperty("기관")
        private String institution;
        @JsonProperty("기간")
        private String period;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class License {
        @JsonProperty("이름")
        private String name;
        @JsonProperty("기관")
        private String issuer;
        @JsonProperty("취득일")
        private String date;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Activity {
        @JsonProperty("종류")
        private String kind;
        @JsonProperty("이름")
        private String name;
        @JsonProperty("기간")
        private String period;
    }

    /** 빠진 칸은 빈 값으로 채운다. 실패시키지 않는다 (가릴 것이 없을 뿐이다) */
    public void validate() {
        names = list(names);
        otherNames = list(otherNames);
        births = list(births);
        phones = list(phones);
        emails = list(emails);
        addresses = list(addresses);
        links = list(links);
        salaries = list(salaries);
        others = list(others);
        education = items(education);
        careers = items(careers);
        trainings = items(trainings);
        certificates = items(certificates);
        activities = items(activities);
        careers.forEach(j -> {
            j.setCompany(list(j.getCompany()));
            // 재직 기간("2024.08 ~ 2026.06")과 근무 기간("1년 11개월")을 서로 바꿔 적는 일이 있다
            if (!hasDate(j.getPeriod()) && hasDate(j.getDuration())) {
                String period = j.getDuration();
                j.setDuration(j.getPeriod());
                j.setPeriod(period);
            }
        });
    }

    private static final Pattern DATE = Pattern.compile("\\d{2,4}\\s*[./년-]\\s*\\d{1,2}");

    private static boolean hasDate(String value) {
        return value != null && DATE.matcher(value).find();
    }

    /** 찾은 값을 가릴 목록으로. 부서명과 경력 기간은 넣지 않고, 활동은 병역만 넣는다 */
    public List<PiiResult.Item> toItems() {
        List<PiiResult.Item> out = new ArrayList<>();
        names.forEach(v -> add(out, Type.APPLICANT_NAME, v));
        otherNames.forEach(v -> add(out, Type.OTHER_NAME, v));
        add(out, Type.SENSITIVE, gender);
        births.forEach(v -> add(out, Type.BIRTH, v));
        phones.forEach(v -> add(out, Type.PHONE, v));
        emails.forEach(v -> add(out, Type.EMAIL, v));
        addresses.forEach(v -> add(out, Type.ADDRESS, v));
        links.forEach(v -> add(out, Type.URL, v));
        for (School s : education) {
            add(out, Type.SCHOOL, s.getSchool());
            add(out, Type.EDUCATION, s.getMajor(), s.getStatus(), s.getPeriod(), s.getGpa());
        }
        for (Job j : careers) {
            j.getCompany().forEach(v -> add(out, Type.COMPANY, v));
            // 재직 기간·근무 기간·총 경력은 가리지 않는다: 근거 분석이 경력 개월과 프로젝트 시기를 이 글로 판단한다
            add(out, Type.CAREER, j.getPosition());
        }
        salaries.forEach(v -> add(out, Type.SALARY, v));
        for (Course c : trainings) {
            add(out, Type.TRAINING, c.getName(), c.getInstitution(), c.getPeriod());
        }
        for (License l : certificates) {
            add(out, Type.CERTIFICATE, l.getName(), l.getIssuer(), l.getDate());
        }
        for (Activity a : activities) {
            if (toActivity(a).isMilitary()) {
                add(out, Type.SENSITIVE, a.getName(), a.getPeriod());
            }
        }
        others.forEach(v -> add(out, Type.SENSITIVE, v));
        return out;
    }

    /** 화면용 프로필. 한 값짜리 칸은 처음 적은 표기를 쓰고, 회사는 첫 표기를 쓴다. 연봉은 넣지 않는다 */
    public CandidateProfile toProfile() {
        CandidateProfile p = new CandidateProfile();
        p.setGender(text(gender));
        p.setBirth(first(births));
        p.setPhone(first(phones));
        p.setEmail(first(emails));
        p.setAddress(first(addresses));
        p.setLink(first(links));
        p.setTotalCareer(text(totalCareer));
        for (School s : education) {
            CandidateProfile.Education e = new CandidateProfile.Education();
            e.setSchool(text(s.getSchool()));
            e.setMajor(text(s.getMajor()));
            e.setStatus(text(s.getStatus()));
            e.setPeriod(text(s.getPeriod()));
            e.setGpa(text(s.getGpa()));
            p.getEducation().add(e);
        }
        for (Job j : careers) {
            CandidateProfile.Career c = new CandidateProfile.Career();
            c.setCompany(first(j.getCompany()));
            c.setDepartment(text(j.getDepartment()));
            c.setPosition(text(j.getPosition()));
            c.setPeriod(text(j.getPeriod()));
            c.setDuration(text(j.getDuration()));
            p.getCareers().add(c);
        }
        for (Course c : trainings) {
            CandidateProfile.Training t = new CandidateProfile.Training();
            t.setName(text(c.getName()));
            t.setInstitution(text(c.getInstitution()));
            t.setPeriod(text(c.getPeriod()));
            p.getTrainings().add(t);
        }
        for (License l : certificates) {
            CandidateProfile.Certificate c = new CandidateProfile.Certificate();
            c.setName(text(l.getName()));
            c.setIssuer(text(l.getIssuer()));
            c.setDate(text(l.getDate()));
            p.getCertificates().add(c);
        }
        activities.forEach(a -> p.getActivities().add(toActivity(a)));
        return p;
    }

    /** 회사마다 적힌 표기들. 다른 표기도 같은 "A사"로 가리기 위해 쓴다 */
    public List<List<String>> companyNames() {
        return careers.stream().map(Job::getCompany).filter(names -> !names.isEmpty()).toList();
    }

    private static CandidateProfile.Activity toActivity(Activity a) {
        CandidateProfile.Activity v = new CandidateProfile.Activity();
        v.setKind(text(a.getKind()));
        v.setName(text(a.getName()));
        v.setPeriod(text(a.getPeriod()));
        return v;
    }

    private static void add(List<PiiResult.Item> out, Type type, String... values) {
        Arrays.stream(values).filter(v -> v != null && !v.isBlank()).forEach(v -> out.add(new PiiResult.Item(type, v.strip())));
    }

    private static List<String> list(List<String> values) {
        return values == null ? new ArrayList<>()
                : new ArrayList<>(values.stream().filter(v -> v != null && !v.isBlank()).map(String::strip).toList());
    }

    private static <T> List<T> items(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values.stream().filter(v -> v != null).toList());
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }

    private static Map<String, Object> array(Object... fieldsAndTypes) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (int i = 0; i < fieldsAndTypes.length; i += 2) {
            props.put((String) fieldsAndTypes[i], fieldsAndTypes[i + 1]);
        }
        return Map.of("type", "array", "items",
                Map.of("type", "object", "required", List.copyOf(props.keySet()), "properties", props));
    }
}
