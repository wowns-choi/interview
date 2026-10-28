package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.interview.domain.ChatMessage;

@Mapper
public interface ChatMapper {
    void insert(ChatMessage message);

    List<ChatMessage> find(@Param("candidateId") Long candidateId);

    void delete(@Param("candidateId") Long candidateId);
}
