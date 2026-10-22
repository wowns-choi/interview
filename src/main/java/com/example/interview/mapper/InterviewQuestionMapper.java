package com.example.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.interview.domain.InterviewQuestion;

@Mapper
public interface InterviewQuestionMapper {
    List<InterviewQuestion> findByCandidateId(Long candidateId);

    int maxSortOrder(Long candidateId);

    void insert(InterviewQuestion question);

    void deleteByCandidateId(Long candidateId);

    /** @return 갱신된 행 수 (다른 지원자의 질문이면 0) */
    int updateAnswer(@Param("candidateId") Long candidateId, @Param("id") Long id, @Param("answer") String answer);

    /** @return 1이면 채움, 0이면 이미 답변이 있어 그대로 둠 */
    int fillAnswer(@Param("candidateId") Long candidateId, @Param("id") Long id, @Param("answer") String answer,
            @Param("excerpt") String excerpt);

    int updateChecked(@Param("candidateId") Long candidateId, @Param("id") Long id, @Param("checked") boolean checked);

    int updateContent(@Param("candidateId") Long candidateId, @Param("id") Long id, @Param("content") String content);

    int updatePlace(@Param("candidateId") Long candidateId, @Param("id") Long id, @Param("section") String section,
            @Param("sortOrder") int sortOrder);

    /** 추출해 둔 질문을 질문지에 넣는다 */
    int merge(@Param("candidateId") Long candidateId, @Param("id") Long id, @Param("section") String section,
            @Param("sortOrder") int sortOrder);

    int delete(@Param("candidateId") Long candidateId, @Param("id") Long id);
}
