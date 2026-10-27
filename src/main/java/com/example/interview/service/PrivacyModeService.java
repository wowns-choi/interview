package com.example.interview.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.interview.mapper.AppSettingMapper;

import lombok.RequiredArgsConstructor;

/**
 * 개인정보 엄격 보호 모드 (설정 탭, user 2026-10-27). 켜면 지원자를 한 명씩만 올리고, 가린 이력서를 사람이 확인·수정한 뒤에야
 * 외부 AI로 적합도 검사를 한다. 끄면 가리기에서 검사까지 한 번에 간다.
 */
@Service
@RequiredArgsConstructor
public class PrivacyModeService {

    private static final String KEY = "strict_privacy";

    private final AppSettingMapper settings;

    public boolean isStrict() {
        return "Y".equals(settings.find(KEY));
    }

    @Transactional
    public void setStrict(boolean strict) {
        String value = strict ? "Y" : "N";
        if (settings.update(KEY, value) == 0) {
            settings.insert(KEY, value);
        }
    }
}
