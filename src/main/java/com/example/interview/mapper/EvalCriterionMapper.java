package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.example.interview.domain.EvalCriterion;

@Mapper
public interface EvalCriterionMapper {
    List<EvalCriterion> findByPositionId(Long positionId);

    List<EvalCriterion> findAllByPositionId(Long positionId);

    EvalCriterion findById(Long id);

    int maxSortOrder(Long positionId);

    void insert(EvalCriterion criterion);

    void update(EvalCriterion criterion);

    void delete(Long id);

    void deleteByPositionId(Long positionId);
}
