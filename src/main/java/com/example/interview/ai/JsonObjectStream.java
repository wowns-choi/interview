package com.example.interview.ai;

import java.util.function.Consumer;

/**
 * 스트림으로 오는 JSON 글에서 정해진 깊이의 객체가 하나 끝날 때마다 그 글을 넘긴다.
 * {"items":[{...},{...}]}에서 항목은 깊이 2다 (가장 바깥 객체가 1). 문자열 안의 괄호와 이스케이프는 센다.
 */
public class JsonObjectStream implements Consumer<String> {

    private final int depth;
    private final Consumer<String> onObject;
    private final StringBuilder current = new StringBuilder();
    private int braces;
    private boolean inString;
    private boolean escape;

    public JsonObjectStream(int depth, Consumer<String> onObject) {
        this.depth = depth;
        this.onObject = onObject;
    }

    @Override
    public void accept(String chunk) {
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            if (braces >= depth) {
                current.append(c);
            }
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                braces++;
                if (braces == depth) {
                    current.setLength(0);
                    current.append('{');
                }
            } else if (c == '}') {
                if (braces == depth) {
                    onObject.accept(current.toString());
                    current.setLength(0);
                }
                braces--;
            }
        }
    }
}
