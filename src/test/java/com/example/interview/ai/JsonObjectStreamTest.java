package com.example.interview.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class JsonObjectStreamTest {

    @Test
    void emitsEachItemAsSoonAsItCloses() {
        List<String> got = new ArrayList<>();
        JsonObjectStream s = new JsonObjectStream(2, got::add);
        String json = "{\"items\":[{\"q\":\"괄호 } 와 { 그리고 \\\"따옴표\\\"\",\"a\":\"1\"},{\"q\":\"둘\"}]}";
        // 조각이 어디서 끊겨도 같은 결과
        for (int i = 0; i < json.length(); i += 3) {
            s.accept(json.substring(i, Math.min(json.length(), i + 3)));
            if (i == 3) {
                assertThat(got).isEmpty();
            }
        }
        assertThat(got).containsExactly("{\"q\":\"괄호 } 와 { 그리고 \\\"따옴표\\\"\",\"a\":\"1\"}", "{\"q\":\"둘\"}");
    }
}
