package com.example.interview.domain;

import lombok.Data;

@Data
public class PositionImage {
    private Long id;
    private Long positionId;
    private String contentType;
    private byte[] imageData;
    private int sortOrder;
}
