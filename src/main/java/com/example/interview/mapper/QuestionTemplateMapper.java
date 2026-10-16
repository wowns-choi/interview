package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.interview.domain.QuestionTemplate;

@Mapper
public interface QuestionTemplateMapper {
    /** 섹션 순서 → 섹션 안 순서 */
    List<QuestionTemplate> findAll();

    /** 질문지에 넣을 사용 중인 질문, findAll과 같은 순서 */
    List<QuestionTemplate> findActive();

    QuestionTemplate findById(Long id);

    int nextSortOrder(Long sectionId);

    void insert(QuestionTemplate template);

    void updateContent(@Param("id") Long id, @Param("content") String content);

    void updatePlacement(@Param("id") Long id, @Param("sectionId") Long sectionId, @Param("sortOrder") int sortOrder);

    void delete(Long id);
}
