package com.example.interview.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.interview.config.AiProperties;
import com.example.interview.service.BusinessException;

/**
 * 외부 AI API 키. 시작할 때는 설정(환경변수 또는 config/application-local.properties)의 값을 쓰고,
 * 설정 탭에서 바꾸면 그 파일(.gitignore 대상)에 저장하고 재시작 없이 바로 쓴다.
 */
@Component
public class ApiKeyStore {

    static final Path FILE = Path.of("config", "application-local.properties");
    private static final Pattern KEY_SHAPE = Pattern.compile("[A-Za-z0-9_\\-.]{20,300}");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final AiProperties props;
    private final Path file;
    private volatile String key;

    @Autowired
    public ApiKeyStore(AiProperties props) {
        this(props, FILE);
    }

    ApiKeyStore(AiProperties props, Path file) {
        this.props = props;
        this.file = file;
        this.key = props.apiKey();
    }

    public String get() {
        return key;
    }

    public boolean isSet() {
        return key != null && !key.isBlank();
    }

    /** 화면용: 앞 7자와 끝 4자만 */
    public String getMasked() {
        if (!isSet()) {
            return "";
        }
        return key.length() <= 12 ? "••••" : key.substring(0, 7) + "…" + key.substring(key.length() - 4);
    }

    /** 키가 맞는지 모델 목록(무료)으로 확인한 뒤 파일에 저장하고 바로 쓴다. */
    public void save(String newKey) {
        String k = newKey == null ? "" : newKey.strip();
        if (!KEY_SHAPE.matcher(k).matches()) {
            throw new BusinessException("API 키 형식이 아닙니다. 복사한 키를 그대로 붙여넣으세요.");
        }
        check(k);
        write(k);
        key = k;
    }

    /** GET {base-url}/models. 토큰을 쓰지 않는다. */
    void check(String k) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(props.baseUrl().replaceAll("/+$", "") + "/models"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + k)
                .GET().build();
        int status;
        try {
            status = HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            throw new BusinessException("키를 확인하지 못했습니다. 인터넷 연결을 확인하세요.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("키 확인이 중단되었습니다.");
        }
        if (status == 401 || status == 403) {
            throw new BusinessException("올바르지 않은 API 키입니다.");
        }
        if (status >= 400) {
            throw new BusinessException("키를 확인하지 못했습니다. (HTTP " + status + ")");
        }
    }

    /** ai.api-key 줄만 바꾸고 나머지(DB 비밀번호 등)는 그대로 둔다. */
    void write(String k) {
        try {
            List<String> lines = Files.exists(file)
                    ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8)) : new ArrayList<>();
            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).strip().startsWith("ai.api-key")) {
                    lines.set(i, "ai.api-key=" + k);
                    replaced = true;
                }
            }
            if (!replaced) {
                if (lines.isEmpty()) {
                    lines.add("# 로컬 전용 비밀 설정. .gitignore에 등록되어 있으니 커밋하지 말 것.");
                }
                lines.add("ai.api-key=" + k);
            }
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("키를 저장하지 못했습니다: " + e.getMessage());
        }
    }
}
