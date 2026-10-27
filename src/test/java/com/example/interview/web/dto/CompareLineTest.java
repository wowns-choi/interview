package com.example.interview.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompareLineTest {

    @Test
    void requirementSplitsOffParenthesis() {
        CompareLine noted = new CompareLine("학사", "최종 학력", "학력무관(예정자 가능)", "공고", null, true, null);
        assertThat(noted.getRequiredMain()).isEqualTo("학력무관");
        assertThat(noted.getRequiredNote()).isEqualTo("예정자 가능");
        CompareLine plain = new CompareLine("학사", "최종 학력", "대졸(4년제) 이상", "공고", null, true, null);
        assertThat(plain.getRequiredMain()).isEqualTo("대졸(4년제) 이상");
        assertThat(plain.getRequiredNote()).isEmpty();
        CompareLine none = new CompareLine("학사", "최종 학력", null, "공고", null, null, null);
        assertThat(none.getRequiredMain()).isNull();
        assertThat(none.getRequiredNote()).isEmpty();
    }
}
