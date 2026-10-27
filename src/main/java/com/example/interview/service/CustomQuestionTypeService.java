package com.example.interview.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.interview.domain.CustomQuestionType;
import com.example.interview.domain.QuestionType;
import com.example.interview.mapper.CustomQuestionTypeMapper;

import lombok.RequiredArgsConstructor;

/** 면접 질문 추출 창에서 면접관이 직접 만든 질문 유형 (모든 지원자에 공통) */
@Service
@RequiredArgsConstructor
public class CustomQuestionTypeService {

    static final int MAX_LENGTH = 60;

    private final CustomQuestionTypeMapper mapper;

    public List<CustomQuestionType> list() {
        return mapper.findAll();
    }

    /** 같은 이름(대소문자 무시)이 이미 있으면 그것을 돌려준다. 기본 유형 이름은 만들지 않는다 */
    public CustomQuestionType add(String name) {
        String clean = name == null ? "" : name.replaceAll("\s+", " ").strip();
        if (clean.isEmpty()) {
            throw new BusinessException("유형 이름을 적으세요.");
        }
        if (clean.length() > MAX_LENGTH) {
            clean = clean.substring(0, MAX_LENGTH);
        }
        if (QuestionType.ofLabel(clean) != null) {
            throw new BusinessException("기본 유형과 이름이 같습니다.");
        }
        String key = clean;
        CustomQuestionType same = mapper.findAll().stream()
                .filter(t -> t.getName().equalsIgnoreCase(key)).findFirst().orElse(null);
        if (same != null) {
            return same;
        }
        CustomQuestionType type = new CustomQuestionType();
        type.setName(clean);
        mapper.insert(type);
        return type;
    }

    public void delete(Long id) {
        if (mapper.delete(id) == 0) {
            throw new NotFoundException("유형을 찾을 수 없습니다: " + id);
        }
    }
}
