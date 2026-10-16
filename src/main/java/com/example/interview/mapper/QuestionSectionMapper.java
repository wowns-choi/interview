package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.interview.domain.QuestionSection;

@Mapper
public interface QuestionSectionMapper {
    List<QuestionSection> findAll();

    QuestionSection findById(Long id);

    int nextSortOrder();

    void insert(QuestionSection section);

    void rename(@Param("id") Long id, @Param("name") String name);

    void updateSortOrder(@Param("id") Long id, @Param("sortOrder") int sortOrder);

    /** 섹션의 질문도 함께 지워진다 (ON DELETE CASCADE) */
    void delete(Long id);
}
