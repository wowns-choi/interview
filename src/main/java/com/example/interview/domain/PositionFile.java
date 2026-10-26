package com.example.interview.domain;

import lombok.Data;

/** 포지션에 올린 원본 파일. 화면 목록에서는 fileData 없이 읽는다 */
@Data
public class PositionFile {
    private Long id;
    private Long positionId;
    private String fileName;
    private String contentType;
    private byte[] fileData;
    private int sortOrder;
}
