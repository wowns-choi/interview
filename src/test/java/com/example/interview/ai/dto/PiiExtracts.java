package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.List;

import com.example.interview.domain.CandidateProfile;

/** 테스트용: 프로필과 가릴 값으로 로컬 AI 응답(PiiExtract)을 만든다 */
public final class PiiExtracts {

    private PiiExtracts() {
    }

    public static PiiExtract of(CandidateProfile p, PiiResult.Item... items) {
        PiiExtract r = new PiiExtract();
        r.validate();
        if (p != null) {
            r.setGender(p.getGender());
            addIfPresent(r.getBirths(), p.getBirth());
            addIfPresent(r.getPhones(), p.getPhone());
            addIfPresent(r.getEmails(), p.getEmail());
            addIfPresent(r.getAddresses(), p.getAddress());
            addIfPresent(r.getLinks(), p.getLink());
            r.setTotalCareer(p.getTotalCareer());
            addIfPresent(r.getSalaries(), p.getLastSalary());
            addIfPresent(r.getSalaries(), p.getDesiredSalary());
            for (CandidateProfile.Education e : p.getEducation()) {
                PiiExtract.School s = new PiiExtract.School();
                s.setSchool(e.getSchool());
                s.setMajor(e.getMajor());
                s.setStatus(e.getStatus());
                s.setPeriod(e.getPeriod());
                s.setGpa(e.getGpa());
                r.getEducation().add(s);
            }
            for (CandidateProfile.Career c : p.getCareers()) {
                r.getCareers().add(job(c, c.getCompany()));
                addIfPresent(r.getSalaries(), c.getSalary());
            }
            for (CandidateProfile.Training t : p.getTrainings()) {
                PiiExtract.Course c = new PiiExtract.Course();
                c.setName(t.getName());
                c.setInstitution(t.getInstitution());
                c.setPeriod(t.getPeriod());
                r.getTrainings().add(c);
            }
            for (CandidateProfile.Certificate c : p.getCertificates()) {
                PiiExtract.License l = new PiiExtract.License();
                l.setName(c.getName());
                l.setIssuer(c.getIssuer());
                l.setDate(c.getDate());
                r.getCertificates().add(l);
            }
            for (CandidateProfile.Activity a : p.getActivities()) {
                PiiExtract.Activity v = new PiiExtract.Activity();
                v.setKind(a.getKind());
                v.setName(a.getName());
                v.setPeriod(a.getPeriod());
                r.getActivities().add(v);
            }
        }
        for (PiiResult.Item item : items) {
            switch (item.getType()) {
                case APPLICANT_NAME -> r.getNames().add(item.getText());
                case OTHER_NAME -> r.getOtherNames().add(item.getText());
                case PHONE -> r.getPhones().add(item.getText());
                case EMAIL -> r.getEmails().add(item.getText());
                case BIRTH -> r.getBirths().add(item.getText());
                case ADDRESS -> r.getAddresses().add(item.getText());
                case URL -> r.getLinks().add(item.getText());
                case SALARY -> r.getSalaries().add(item.getText());
                case SENSITIVE -> r.getOthers().add(item.getText());
                default -> throw new IllegalArgumentException("경력·학력 같은 값은 프로필로 넣는다: " + item.getType());
            }
        }
        r.validate();
        return r;
    }

    /** 한 회사의 여러 표기 */
    public static PiiExtract.Job job(CandidateProfile.Career c, String... companyNames) {
        PiiExtract.Job j = new PiiExtract.Job();
        j.setCompany(new ArrayList<>(List.of(companyNames)));
        j.setDepartment(c.getDepartment());
        j.setPosition(c.getPosition());
        j.setPeriod(c.getPeriod());
        j.setDuration(c.getDuration());
        return j;
    }

    private static void addIfPresent(List<String> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(value);
        }
    }
}
