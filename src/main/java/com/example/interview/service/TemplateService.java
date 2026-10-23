package com.example.interview.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.interview.domain.QuestionSection;
import com.example.interview.domain.QuestionTemplate;
import com.example.interview.mapper.InterviewQuestionMapper;
import com.example.interview.mapper.QuestionSectionMapper;
import com.example.interview.mapper.QuestionTemplateMapper;
import com.example.interview.web.dto.SectionBoard;

import lombok.RequiredArgsConstructor;

/** 공통 질문 설정: 섹션과 질문의 추가·수정·삭제·순서 변경 */
@Service
@RequiredArgsConstructor
public class TemplateService {

    static final int SECTION_NAME_MAX = 100;
    static final int CONTENT_MAX = 2000;

    private final QuestionSectionMapper sectionMapper;
    private final QuestionTemplateMapper templateMapper;
    private final InterviewQuestionMapper questionMapper;

    /** 섹션 순서대로, 각 섹션의 질문을 순서대로 */
    public List<SectionBoard> board() {
        Map<Long, SectionBoard> boards = new LinkedHashMap<>();
        for (QuestionSection s : sectionMapper.findAll()) {
            boards.put(s.getId(), new SectionBoard(s, new ArrayList<>()));
        }
        for (QuestionTemplate t : templateMapper.findAll()) {
            SectionBoard b = boards.get(t.getSectionId());
            if (b != null) {
                b.getQuestions().add(t);
            }
        }
        return new ArrayList<>(boards.values());
    }

    @Transactional
    public QuestionSection createSection(String name) {
        QuestionSection s = new QuestionSection();
        s.setName(sectionName(name));
        s.setSortOrder(sectionMapper.nextSortOrder());
        sectionMapper.insert(s);
        return s;
    }

    @Transactional
    public void renameSection(Long id, String name) {
        section(id);
        sectionMapper.rename(id, sectionName(name));
    }

    @Transactional
    public void deleteSection(Long id) {
        section(id);
        sectionMapper.delete(id);
    }

    @Transactional
    public QuestionTemplate createQuestion(Long sectionId, String content) {
        section(sectionId);
        QuestionTemplate t = new QuestionTemplate();
        t.setSectionId(sectionId);
        t.setContent(content(content));
        t.setSortOrder(templateMapper.nextSortOrder(sectionId));
        templateMapper.insert(t);
        return t;
    }

    @Transactional
    public void updateQuestion(Long id, String content) {
        question(id);
        templateMapper.updateContent(id, content(content));
    }

    @Transactional
    public void setMustAsk(Long id, boolean mustAsk) {
        question(id);
        templateMapper.updateMustAsk(id, mustAsk);
        questionMapper.updateMustAskByTemplate(id, mustAsk);
    }

    @Transactional
    public void deleteQuestion(Long id) {
        question(id);
        templateMapper.delete(id);
    }

    /**
     * 드래그앤드롭 결과를 그대로 저장한다. 화면의 섹션 순서와 각 섹션 안의 질문 순서를 받는다.
     * 질문이 다른 섹션으로 옮겨졌으면 섹션도 바뀐다. 모든 섹션과 질문이 정확히 한 번씩 있어야 한다.
     */
    @Transactional
    public void reorder(List<SectionOrder> order) {
        Set<Long> sectionIds = new HashSet<>();
        sectionMapper.findAll().forEach(s -> sectionIds.add(s.getId()));
        Set<Long> questionIds = new HashSet<>();
        templateMapper.findAll().forEach(t -> questionIds.add(t.getId()));

        Set<Long> seenSections = new HashSet<>();
        Set<Long> seenQuestions = new HashSet<>();
        for (SectionOrder so : order) {
            if (so.sectionId() == null || !sectionIds.contains(so.sectionId()) || !seenSections.add(so.sectionId())) {
                throw new BusinessException("화면이 최신 상태가 아닙니다. 새로고침 후 다시 시도하세요.");
            }
            for (Long qid : so.questionIds() == null ? List.<Long>of() : so.questionIds()) {
                if (!questionIds.contains(qid) || !seenQuestions.add(qid)) {
                    throw new BusinessException("화면이 최신 상태가 아닙니다. 새로고침 후 다시 시도하세요.");
                }
            }
        }
        if (!seenSections.equals(sectionIds) || !seenQuestions.equals(questionIds)) {
            throw new BusinessException("화면이 최신 상태가 아닙니다. 새로고침 후 다시 시도하세요.");
        }

        int sectionOrder = 1;
        for (SectionOrder so : order) {
            sectionMapper.updateSortOrder(so.sectionId(), sectionOrder++);
            int questionOrder = 1;
            for (Long qid : so.questionIds() == null ? List.<Long>of() : so.questionIds()) {
                templateMapper.updatePlacement(qid, so.sectionId(), questionOrder++);
            }
        }
    }

    public record SectionOrder(Long sectionId, List<Long> questionIds) {
    }

    private QuestionSection section(Long id) {
        QuestionSection s = id == null ? null : sectionMapper.findById(id);
        if (s == null) {
            throw new NotFoundException("섹션을 찾을 수 없습니다: " + id);
        }
        return s;
    }

    private QuestionTemplate question(Long id) {
        QuestionTemplate t = id == null ? null : templateMapper.findById(id);
        if (t == null) {
            throw new NotFoundException("질문을 찾을 수 없습니다: " + id);
        }
        return t;
    }

    private static String sectionName(String name) {
        String s = name == null ? "" : name.strip();
        if (s.isEmpty()) {
            throw new BusinessException("섹션 이름을 입력하세요.");
        }
        if (s.length() > SECTION_NAME_MAX) {
            throw new BusinessException("섹션 이름은 " + SECTION_NAME_MAX + "자까지 쓸 수 있습니다.");
        }
        return s;
    }

    private static String content(String content) {
        String s = content == null ? "" : content.strip();
        if (s.isEmpty()) {
            throw new BusinessException("질문 내용을 입력하세요.");
        }
        if (s.length() > CONTENT_MAX) {
            throw new BusinessException("질문은 " + CONTENT_MAX + "자까지 쓸 수 있습니다.");
        }
        return s;
    }
}
