package com.example.interview.ai.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.example.interview.ai.AiResponseInvalidException;

import lombok.Data;

/** 보고서 질문-답변의 새 순서 (보낸 번호, 1부터) */
@Data
public class ReportSortResult {

    private List<Integer> order;

    /** 없는 번호·중복은 버리고, 빠진 번호는 원래 순서대로 뒤에 붙인다 */
    public void validate(int count) {
        if (order == null || order.isEmpty()) {
            throw new AiResponseInvalidException("order 배열이 없습니다.");
        }
        Set<Integer> kept = new LinkedHashSet<>();
        order.stream().filter(n -> n != null && n >= 1 && n <= count).forEach(kept::add);
        for (int n = 1; n <= count; n++) {
            kept.add(n);
        }
        order = new ArrayList<>(kept);
    }
}
