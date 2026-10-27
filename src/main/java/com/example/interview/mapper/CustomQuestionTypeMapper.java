package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.example.interview.domain.CustomQuestionType;

@Mapper
public interface CustomQuestionTypeMapper {
    List<CustomQuestionType> findAll();

    void insert(CustomQuestionType type);

    int delete(Long id);
}
