package com.example.interview.ai;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.interview.domain.AiCallLog;
import com.example.interview.mapper.AiCallLogMapper;

import lombok.RequiredArgsConstructor;

/** 분석 트랜잭션이 롤백돼도 호출 기록은 남도록 별도 트랜잭션으로 저장한다. */
@Component
@RequiredArgsConstructor
public class AiCallLogger {

    private final AiCallLogMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AiCallLog log) {
        mapper.insert(log);
    }
}
