package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.interview.domain.Position;
import com.example.interview.domain.PositionStatus;
import com.example.interview.web.dto.PositionSummary;

@Mapper
public interface PositionMapper {
    Position findById(Long id);

    List<PositionSummary> findRecent(@Param("limit") int limit, @Param("offset") int offset);

    void insert(Position position);

    void updateTitle(@Param("id") Long id, @Param("title") String title);

    void rename(@Param("id") Long id, @Param("title") String title);

    void updateImageText(@Param("id") Long id, @Param("imageText") String imageText);

    void updateHiring(@Param("id") Long id,
            @Param("requiredExperience") String requiredExperience,
            @Param("careerMinMonths") Integer careerMinMonths, @Param("careerMaxMonths") Integer careerMaxMonths,
            @Param("requiredEducation") String requiredEducation, @Param("requiredMajor") String requiredMajor);

    void updateConditions(@Param("id") Long id,
            @Param("requiredExperience") String requiredExperience, @Param("requiredEducation") String requiredEducation,
            @Param("requiredMajor") String requiredMajor);

    void updateCareerRange(@Param("id") Long id, @Param("min") Integer min, @Param("max") Integer max);

    void updateStatus(@Param("id") Long id, @Param("status") PositionStatus status);

    void updateExclusions(@Param("id") Long id, @Param("excludeCareer") boolean excludeCareer,
            @Param("excludeEducation") boolean excludeEducation);

    /** 지원자·평가항목·근거·질문까지 함께 지워진다 (ON DELETE CASCADE) */
    void delete(Long id);
}
