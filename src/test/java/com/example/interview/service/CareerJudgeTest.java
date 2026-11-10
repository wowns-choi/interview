package com.example.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.example.interview.ai.AiClient;
import com.example.interview.ai.PromptLoader;
import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.Position;
import com.example.interview.mapper.CandidateMapper;

class CareerJudgeTest {

    private final PositionService positions = mock(PositionService.class);
    private final CareerEvalService service = new CareerEvalService(mock(AiClient.class), new PromptLoader(), positions,
            mock(CandidateMapper.class));

    @Test
    void comparesRelevantMonthsWithTheStoredRange() {
        assertThat(judge("경력 10년 이상", 120, null, 28).getCareerMeets()).isEqualTo("NOT_MET");
        assertThat(judge("경력 2년 이상", 24, null, 28).getCareerMeets()).isEqualTo("MET");
        assertThat(judge("신입·경력 3년 ↓", null, 36, 40).getCareerMeets()).isEqualTo("NOT_MET");
        assertThat(judge("경력무관", null, null, 0).getCareerMeets()).isEqualTo("MET");
        assertThat(judge("", null, null, 28).getCareerMeets()).isEqualTo("UNKNOWN");
    }

    @Test
    void doesNotJudgeAWordedRequirementWithoutMonths() {
        // 개월을 저장하기 전 포지션: "경력 10년 이상"이 경력무관으로 충족되던 문제 (사용자 2026-11-10)
        assertThat(judge("경력 10년 이상", null, null, 28)).isNull();
    }

    private CareerEvalResult judge(String required, Integer min, Integer max, Integer relevant) {
        Position p = new Position();
        p.setId(1L);
        p.setRequiredExperience(required);
        p.setCareerMinMonths(min);
        p.setCareerMaxMonths(max);
        when(positions.get(1L)).thenReturn(p);
        Candidate c = new Candidate();
        c.setPositionId(1L);
        return service.judgeCareer(c, relevant, relevant);
    }
}
