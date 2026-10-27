package com.example.interview.domain;

import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 추출 질문 유형. 앞의 셋은 이력서에 했다고 쓴 일(근거)에 대해, EXPERIENCE는 포지션이 요구하지만 근거가 약한 기술에 대해 묻는다.
 * 화면에는 면접관이 바로 알아보도록 말투 그대로의 이름을 쓴다. CUSTOM은 면접관이 직접 적은 유형, GAP은 예전 질문지에만 남은 값이다.
 */
@Getter
@RequiredArgsConstructor
public enum QuestionType {
    VERIFY("거짓말 판독기", true),
    DEPTH("어디까지 알고 한 거야?", true),
    CHOICE("그 기술 왜 선택했어?", true),
    EXPERIENCE("해본 적 있어?", false),
    /** 면접관이 직접 적은 유형. 이름은 interview_question.type_label에 둔다 */
    CUSTOM("직접 적은 유형", false),
    GAP("해본 적 있어?", false);

    /** 추출할 때 고를 수 있는 유형 (화면 순서) */
    public static final List<QuestionType> SELECTABLE = List.of(VERIFY, DEPTH, CHOICE, EXPERIENCE);

    private final String label;
    /** 이력서 근거에 대한 질문인지 */
    private final boolean claim;

    /** 고를 수 있는 유형 중 화면 이름이 같은 것 (없으면 null: 면접관이 직접 적은 유형) */
    public static QuestionType ofLabel(String label) {
        return SELECTABLE.stream().filter(t -> t.label.equals(label)).findFirst().orElse(null);
    }

    /** "해본 적 있어?"처럼 해봤는지를 묻는 유형 (꼬리질문은 해봤다고 할 때) */
    public boolean isExperience() {
        return this == EXPERIENCE || this == GAP;
    }
}
