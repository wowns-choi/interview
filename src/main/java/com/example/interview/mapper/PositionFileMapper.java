package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.example.interview.domain.PositionFile;

@Mapper
public interface PositionFileMapper {
    void insert(PositionFile file);

    /** 화면 표시용: 바이트 없이 */
    List<PositionFile> findByPositionId(Long positionId);

    PositionFile findById(Long id);
}
