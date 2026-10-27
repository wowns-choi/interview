package com.example.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CandidateProfileTest {

    @Test
    void levelOnlyEducationHasNoSchoolName() {
        assertThat(school("대학교").isLevelOnly()).isTrue();
        assertThat(school("대학교(4년)").isLevelOnly()).isTrue();
        assertThat(school("고등학교").isLevelOnly()).isTrue();
        assertThat(school("").isLevelOnly()).isTrue();
        assertThat(school("서일대학(2·3년제)").isLevelOnly()).isFalse();
        assertThat(school("학점은행제").isLevelOnly()).isFalse();
        assertThat(school("한빛대학교").isLevelOnly()).isFalse();
    }

    private static CandidateProfile.Education school(String name) {
        CandidateProfile.Education e = new CandidateProfile.Education();
        e.setSchool(name);
        return e;
    }
}
