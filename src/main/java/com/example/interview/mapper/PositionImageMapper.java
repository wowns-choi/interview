package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.example.interview.domain.PositionImage;

@Mapper
public interface PositionImageMapper {
    void insert(PositionImage image);

    /** 이미지 바이트까지 읽는다 (AI 호출용) */
    List<PositionImage> findByPositionId(Long positionId);

    /** 화면 표시용: 바이트 없이 id만 */
    List<Long> findIdsByPositionId(Long positionId);

    PositionImage findById(Long id);
}
