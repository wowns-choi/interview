package com.example.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.AiClient;
import com.example.interview.ai.AiInputTooLongException;
import com.example.interview.ai.PromptLoader;
import com.example.interview.ai.dto.PiiExtract;
import com.example.interview.ai.dto.PiiExtracts;
import com.example.interview.ai.dto.PiiResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.CandidateProfile;
import com.example.interview.resume.PiiLeakChecker;
import com.example.interview.resume.PiiMasker;
import com.example.interview.resume.ResumeProcessingException;

class ResumeAnonymizerTest {

    private final AiClient aiClient = mock(AiClient.class);
    private final ResumeAnonymizer anonymizer = new ResumeAnonymizer(aiClient, new PromptLoader(), new PiiMasker(),
            new PiiLeakChecker());
    private final List<String> sent = new ArrayList<>();

    @Test
    void splitsLongTextAndMasksNamesFoundInAnyChunk() {
        String filler = "Spring Boot 주문 API 개발 경험\n".repeat(400);
        String text = "홍길동\n" + filler + "마지막 장: 홍길동 드림";
        // 첫 조각에서만 이름을 알려준다
        stub(chunk -> chunk.contains("<resume>\n홍길동") ? List.of(item(PiiResult.Type.APPLICANT_NAME, "홍길동")) : List.of());

        ResumeAnonymizer.Result masked = anonymizer.anonymize(1L, text);

        assertThat(sent).hasSizeGreaterThan(1);
        assertThat(sent).allSatisfy(s -> assertThat(s.length()).isLessThanOrEqualTo(ResumeAnonymizer.CHUNK_CHARS + 30));
        assertThat(masked.text()).doesNotContain("홍길동").endsWith("마지막 장: [이름] 드림");
    }

    @Test
    void halvesChunkWhenModelInputIsTruncated() {
        String text = "가나다라마바사 경력 기술\n".repeat(200);
        stub(chunk -> {
            if (chunk.length() > 2000) {
                throw new AiInputTooLongException("잘림");
            }
            return List.of();
        });

        anonymizer.anonymize(1L, text);

        assertThat(sent).hasSizeGreaterThan(2);
        assertThat(sent.subList(1, sent.size())).allSatisfy(s -> assertThat(s.length()).isLessThanOrEqualTo(2000));
    }

    @Test
    void failsWhenLocalAiFails() {
        stub(chunk -> {
            throw new AiInputTooLongException("잘림");
        });

        assertThatThrownBy(() -> anonymizer.anonymize(1L, "짧은 글"))
                .isInstanceOf(ResumeProcessingException.class).hasMessageContaining("Ollama");
    }

    @Test
    void mergesProfilesAndKeepsOnlyValuesFoundInText() {
        CandidateProfile first = new CandidateProfile();
        first.setEducation(List.of(education("한국대학교", "컴퓨터공학과"), education("한국대학교", "컴퓨터공학과")));
        first.setTotalCareer("총 경력 3년");
        CandidateProfile second = new CandidateProfile();
        second.setEducation(List.of(education("서울대학교", "")));
        second.setTotalCareer("총 경력 9년");
        second.setLastSalary("4,200 만원");

        CandidateProfile p = ResumeAnonymizer.verify(ResumeAnonymizer.merge(List.of(first, second)),
                "한국대학교 컴퓨터공학과\n총 경력 3년\n직전 연봉 4,200만원");

        assertThat(p.getEducation()).as("중복은 하나로, 원문에 없는 학교는 버린다")
                .extracting("school", "major").containsExactly(tuple("한국대학교", "컴퓨터공학과"));
        assertThat(p.getTotalCareer()).isEqualTo("총 경력 3년");
        assertThat(p.getLastSalary()).as("공백만 다른 값은 원문에 있는 것으로 본다").isEqualTo("4,200 만원");
    }

    @Test
    void masksAgeEvenWhenLocalAiMissesIt() {
        stub(chunk -> List.of());

        ResumeAnonymizer.Result r = anonymizer.anonymize(1L, "남, (33세) / 만 31세 / 21세기 기술 / 5세대 이동통신 / 30세대 아파트");

        assertThat(r.text()).isEqualTo("남, ([생년월일]) / [생년월일] / 21세기 기술 / 5세대 이동통신 / 30세대 아파트");
    }

    @Test
    void hidesCompanyRankEducationAndTrainingButKeepsCareerPeriodsWorkContentAndCertificates() {
        String text = """
                2024.08 ~ 2026.06  다온테크 연구개발팀 · 사원 · 백엔드
                ∙ 정산 배치 처리 시간 4시간 → 40분 단축
                연봉 3,000만원
                총 2년 3개월 / 직전 연봉 : 3,400 만원
                2014.03 ~ 2020.02 한결대학교 컴퓨터공학 졸업 학점 3.19/4.5
                2024.01 ~ 2024.06 KH 정보교육원 6개월 국비 교육과정 수료
                정보처리기사 2023.11 한국산업인력공단
                """;
        CandidateProfile p = new CandidateProfile();
        CandidateProfile.Career c = new CandidateProfile.Career();
        c.setCompany("다온테크");
        c.setDepartment("연구개발팀");
        c.setPosition("사원");
        c.setPeriod("2024.08 ~ 2026.06");
        p.setCareers(List.of(c));
        p.setTotalCareer("총 2년 3개월");
        CandidateProfile.Education e = education("한결대학교", "컴퓨터공학");
        e.setStatus("졸업");
        e.setPeriod("2014.03 ~ 2020.02");
        p.setEducation(List.of(e));
        CandidateProfile.Training t = new CandidateProfile.Training();
        t.setInstitution("KH 정보교육원");
        t.setName("6개월 국비 교육과정 수료");
        t.setPeriod("2024.01 ~ 2024.06");
        p.setTrainings(List.of(t));
        CandidateProfile.Certificate cert = new CandidateProfile.Certificate();
        cert.setName("정보처리기사");
        cert.setIssuer("한국산업인력공단");
        cert.setDate("2023.11");
        p.setCertificates(List.of(cert));
        // 로컬 AI가 items로는 아무것도 주지 않아도, 옮겨 적은 값과 이름표 붙은 연봉·학점은 가려진다
        stub(chunk -> List.of(), p);

        ResumeAnonymizer.Result r = anonymizer.anonymize(1L, text);

        assertThat(r.text()).isEqualTo("""
                2024.08 ~ 2026.06  [A사] 연구개발팀 · [경력] · 백엔드
                ∙ 정산 배치 처리 시간 4시간 → 40분 단축
                연봉 [연봉]
                총 2년 3개월 / 직전 연봉 : [연봉]
                [학력] [학교] [학력] [학력] 학점 [학력]
                [교육] [교육] [교육]
                정보처리기사 2023.11 한국산업인력공단
                """);
        assertThat(r.profile().getTrainings()).singleElement().extracting("institution", "alias")
                .containsExactly("KH 정보교육원", "교육1");
        assertThat(r.profile().getEducation()).extracting("alias").containsExactly("학교1");
        assertThat(r.profile().getCareers()).extracting("alias").containsExactly("A사");
    }

    @Test
    void masksLabeledBirthDateAndAddressEvenWhenLocalAiMissesThem() {
        stub(chunk -> List.of());

        ResumeAnonymizer.Result r = anonymizer.anonymize(1L,
                "성명 홍길동 생년월일 1993.09.11\n주소 : 경기도 성남시 분당구 가나로 12 (다라동)\nIP 주소 변환 로직 개발");

        assertThat(r.text()).isEqualTo("성명 홍길동 생년월일 [생년월일]\n주소 : [주소]\nIP 주소 변환 로직 개발");
    }

    @Test
    void masksFixedShapeEducationAndPostalCode() {
        stub(chunk -> List.of());

        ResumeAnonymizer.Result r = anonymizer.anonymize(1L,
                "주소 (04512) 서울\n학력 대학교(4년) 졸업\n인정학력 4년제 지역 서울\n미래예술대학(2·3년제) 중퇴\n운영 기간 (4년)");

        assertThat(r.text()).isEqualTo("주소 [주소] [주소]\n학력 대학교[학력] 졸업\n인정학력 [학력]\n미래예술대학[학력] 중퇴\n운영 기간 (4년)");
    }

    @Test
    void masksDisabilityVeteranAndMilitaryFieldsWhereTheyAppear() {
        stub(chunk -> List.of());

        ResumeAnonymizer.Result r = anonymizer.anonymize(1L,
                "기타사항 장애 : 비대상보훈 : 비대상병역 : 군필(만기제대)\n장애여부\t해당 없음\t병역사항: 면제\n"
                        + "보훈대상：대상\n비대상 고객 필터링 기능 개발\n병역 특례 업체에서 근무");

        // 탭과 전각 콜론은 먼저 보통 공백·콜론으로 바뀐다 (normalize)
        assertThat(r.text()).isEqualTo("기타사항 장애 : [개인정보]보훈 : [개인정보]병역 : [개인정보]\n장애여부 [개인정보] 병역사항: [개인정보]\n"
                + "보훈대상:[개인정보]\n비대상 고객 필터링 기능 개발\n병역 특례 업체에서 근무");
    }

    @Test
    void masksMilitaryTableWrittenWithSpacesOnly() {
        stub(chunk -> List.of());

        ResumeAnonymizer.Result r = anonymizer.anonymize(1L,
                "병역 병역필 군별 육군 병과 보병\n계급 병장 복무기간(년월~년월) 2016.02~2017.11\n면제 사유\n"
                        + "군필 여부: 군필, 역종 의무경찰\n면제사유 질병\n주특기 : 통신 군번 15-12345 제대구분 만기\n상사에게 보고하는 절차 개선");

        assertThat(r.text()).isEqualTo("병역 [개인정보] 군별 [개인정보] 병과 [개인정보]\n계급 [개인정보] 복무기간(년월~년월) 2016.02~2017.11\n면제 사유\n"
                + "[개인정보] 여부: [개인정보], 역종 [개인정보]\n면제사유 [개인정보]\n주특기 : [개인정보] 군번 [개인정보] 제대구분 [개인정보]\n상사에게 보고하는 절차 개선");
        assertThat(anonymizer.anonymize(1L, "특기사항: 즉시 출근 가능\n주특기는 백엔드 설계").text())
                .isEqualTo("특기사항: 즉시 출근 가능\n주특기는 백엔드 설계");

        // 제목 줄과 값 줄이 따로인 표 (탭은 공백이 된다. 제목 줄이 가려지는 건 상관없다)
        assertThat(anonymizer.anonymize(1L, "병역사항\t군필여부\t군별\t계급\n\t병역필\t의무경찰\t수경(병장)\t2014.09~2016.06").text())
                .isEqualTo("병역사항 [개인정보]\n [개인정보] [개인정보] [개인정보] 2014.09~2016.06");
    }

    @Test
    void handlesNonBreakingSpacesFromPdf() {
        CandidateProfile p = new CandidateProfile();
        CandidateProfile.Career c = new CandidateProfile.Career();
        c.setDuration("1년 11개월");
        p.setCareers(List.of(c));
        // 로컬 AI는 보통 공백으로 옮겨 적지만, PDF 원문에는 줄바꿈 없는 공백(U+00A0)이 들어 있다
        stub(chunk -> List.of(item(PiiResult.Type.SALARY, "3,400 만원")), p);

        ResumeAnonymizer.Result r = anonymizer.anonymize(1L,
                "학점은행제 면접 후 결정\n직전 연봉 : ３,４００ 만원\n1년 11개월 AI 옥션 프로젝트");

        assertThat(r.text()).isEqualTo("[학력] [연봉]\n직전 연봉 : [연봉]\n1년 11개월 AI 옥션 프로젝트");
        assertThat(r.profile().getCareers()).singleElement().extracting("duration").isEqualTo("1년 11개월");
        assertThat(r.profile().getLastSalary()).as("연봉은 가리기만 하고 저장하지 않는다").isEmpty();
    }

    @Test
    void sameCompanyFromSummaryTableAndProjectBlocksBecomesOneRow() {
        String text = """
                회사명	근무기간	근무부서
                (주)다온테크	2026.06~재직중	연구개발팀
                (주)누리페이	2024.08~2026.05	기술연구소
                회사명	(주)다온테크	부서	연구개발팀
                근무기간	2026.03~재직중	최종연봉	3,400만원
                회사명	(주) 누리페이	부서	기술연구소
                근무기간	2025.06.22~2025.08.21	최종연봉	3,000 만원
                """;
        CandidateProfile p = new CandidateProfile();
        p.setCareers(List.of(career("(주)다온테크", "2026.06~재직중", ""), career("(주)누리페이", "2024.08~2026.05", ""),
                career("(주)다온테크", "2026.03~재직중", "3,400만원"), career("(주) 누리페이", "2025.06.22~2025.08.21", "3,000 만원")));
        stub(chunk -> List.of(), p);

        ResumeAnonymizer.Result r = anonymizer.anonymize(1L, text);

        assertThat(r.profile().getCareers()).extracting("company", "period", "salary").as("연봉은 가리기만 하고 저장하지 않는다").containsExactly(
                tuple("(주)다온테크", "2026.06~재직중", ""),
                tuple("(주)누리페이", "2024.08~2026.05", ""));
        assertThat(r.text()).doesNotContain("다온테크", "누리페이", "3,400", "3,000").contains("2026.03~재직중", "2025.06.22");
    }

    @Test
    void companiesAreMaskedWithTheSameLettersAsTheCareerList() {
        String text = """
                (주)다온테크 2026.06~재직중 / (주)누리페이 2024.08~2026.05
                다온테크에서 정산 배치 개선, 누리 페이에서 결제 API 개발
                자기소개: Nuri Pay 입사 후
                """;
        PiiExtract extract = PiiExtracts.of(null);
        extract.getCareers().add(PiiExtracts.job(career("", "2026.06~재직중", ""), "(주)다온테크"));
        // 같은 회사의 다른 표기도 같은 이름표로 가린다
        extract.getCareers().add(PiiExtracts.job(career("", "2024.08~2026.05", ""), "(주)누리페이", "Nuri Pay"));
        extract.getCareers().add(PiiExtracts.job(career("", "", ""), "다온테크"));
        when(aiClient.callLocal(eq(AiPurpose.PII_DETECT), anyLong(), anyString(), anyString(), any(),
                eq(PiiExtract.class), any())).thenReturn(extract);

        ResumeAnonymizer.Result r = anonymizer.anonymize(1L, text);

        assertThat(r.text()).isEqualTo("""
                [A사] 2026.06~재직중 / [B사] 2024.08~2026.05
                [A사]에서 정산 배치 개선, [B사]에서 결제 API 개발
                자기소개: [B사] 입사 후
                """);
    }

    @Test
    void masksEmergencyContactNumberTheLocalAiMissed() {
        // 로컬 AI가 본인 번호만 찾고 비상연락처(가족 번호)는 놓친 경우
        stub(chunk -> List.of(item(PiiResult.Type.PHONE, "010-1234-5678")));

        ResumeAnonymizer.Result r = anonymizer.anonymize(1L,
                "연락처\t010-1234-5678\t비상연락처(가족)\t010-8765-4321(모친)\n메일 hong@example.com\n사무실 02-345-6789");

        assertThat(r.text()).isEqualTo("연락처 [전화번호] 비상연락처(가족) [전화번호](모친)\n메일 [이메일]\n사무실 [전화번호]");
    }

    @Test
    void companyNamesAlsoWithoutLegalForm() {
        assertThat(ResumeAnonymizer.companyNames("가온소프트(주)")).containsExactly("가온소프트(주)", "가온소프트");
        assertThat(ResumeAnonymizer.companyNames("주식회사 누리페이")).containsExactly("주식회사 누리페이", "누리페이");
        assertThat(ResumeAnonymizer.companyNames("Gaon Soft Co., Ltd.")).containsExactly("Gaon Soft Co., Ltd.", "Gaon Soft");
        assertThat(ResumeAnonymizer.companyNames("누리페이")).containsExactly("누리페이");
    }

    @Test
    void splitKeepsLinesAndCutsOnlyOverlongLines() {
        assertThat(ResumeAnonymizer.split("aa\nbb\ncc\n", 6)).containsExactly("aa\nbb\n", "cc\n");
        assertThat(ResumeAnonymizer.split("a".repeat(19) + "\n" + "b".repeat(9) + "\n", 20))
                .containsExactly("a".repeat(19) + "\n", "b".repeat(9) + "\n");
        assertThat(ResumeAnonymizer.split("a".repeat(19) + "\n" + "b".repeat(3) + "\n", 20))
                .containsExactly("a".repeat(19) + "\n" + "b".repeat(3) + "\n");
        assertThat(ResumeAnonymizer.split("abcdefgh\nij", 3)).containsExactly("abc", "def", "gh\n", "ij");
        assertThat(String.join("", ResumeAnonymizer.split("가나다\n라마\n바사아자\n", 4))).isEqualTo("가나다\n라마\n바사아자\n");
    }

    private void stub(java.util.function.Function<String, List<PiiResult.Item>> answer) {
        stub(answer, null);
    }

    private void stub(java.util.function.Function<String, List<PiiResult.Item>> answer, CandidateProfile profile) {
        when(aiClient.callLocal(eq(AiPurpose.PII_DETECT), anyLong(), anyString(), anyString(), any(),
                eq(PiiExtract.class), any())).thenAnswer(inv -> {
                    String content = inv.getArgument(3);
                    sent.add(content);
                    return PiiExtracts.of(profile, answer.apply(content).toArray(PiiResult.Item[]::new));
                });
    }

    private static PiiResult.Item item(PiiResult.Type type, String text) {
        PiiResult.Item item = new PiiResult.Item();
        item.setType(type);
        item.setText(text);
        return item;
    }

    private static CandidateProfile.Career career(String company, String period, String salary) {
        CandidateProfile.Career c = new CandidateProfile.Career();
        c.setCompany(company);
        c.setPeriod(period);
        c.setSalary(salary);
        return c;
    }

    private static CandidateProfile.Education education(String school, String major) {
        CandidateProfile.Education e = new CandidateProfile.Education();
        e.setSchool(school);
        e.setMajor(major);
        return e;
    }
}
