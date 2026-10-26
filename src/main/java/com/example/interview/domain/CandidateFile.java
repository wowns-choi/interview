package com.example.interview.domain;

import lombok.Data;

/** 지원자가 낸 원본 파일 (가리지 않음). 화면 목록에서는 fileData 없이 읽는다 */
@Data
public class CandidateFile {
    private Long id;
    private Long candidateId;
    private String fileName;
    private String contentType;
    private byte[] fileData;
    private int sortOrder;
}
