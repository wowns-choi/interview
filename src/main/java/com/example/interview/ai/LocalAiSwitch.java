package com.example.interview.ai;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.interview.domain.AiPurpose;
import com.example.interview.mapper.AppSettingMapper;

/**
 * 설정 탭의 「로컬 AI로 처리」 스위치 (사용자 2026-11-09). 켜면 아래 작업을 GPT 대신 로컬 AI(Ollama)로 보낸다.
 * 근거 분석·학력 판단·평가 기준 추출은 켜도 GPT로 간다. 발표에서 같은 작업의 GPT·로컬 시간을 비교하려고 둔다.
 */
@Component
public class LocalAiSwitch {

    private static final String KEY = "local_ai_tasks";

    /** 스위치를 켜면 로컬로 가는 작업 */
    public static final Set<AiPurpose> TASKS = EnumSet.of(AiPurpose.TIMELINE, AiPurpose.ARRANGE, AiPurpose.QUESTION_PLACE,
            AiPurpose.QUESTION, AiPurpose.REPORT_INFO, AiPurpose.INTERVIEW_RECORD,
            AiPurpose.CHAT);

    private final AppSettingMapper settings;

    public LocalAiSwitch(AppSettingMapper settings) {
        this.settings = settings;
    }

    public boolean isOn() {
        return "Y".equals(settings.find(KEY));
    }

    /** 이 작업을 지금 로컬로 보낼지 */
    public boolean routesLocal(AiPurpose purpose) {
        return TASKS.contains(purpose) && isOn();
    }

    @Transactional
    public void set(boolean on) {
        String value = on ? "Y" : "N";
        if (settings.update(KEY, value) == 0) {
            settings.insert(KEY, value);
        }
    }
}
