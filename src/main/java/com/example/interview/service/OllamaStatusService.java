package com.example.interview.service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.example.interview.config.LocalAiProperties;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 설정 탭: 로컬 AI(Ollama)가 설치·실행 중인지, 쓰는 모델을 받아 두었는지 */
@Service
@RequiredArgsConstructor
public class OllamaStatusService {

    public static final String DOWNLOAD_URL = "https://ollama.com/download";
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private final LocalAiProperties props;

    public enum State {
        /** 설치되지 않음 */
        NOT_INSTALLED,
        /** 설치됐지만 실행 중이 아님 */
        NOT_RUNNING,
        /** 실행 중이지만 모델이 없음 */
        NO_MODEL,
        READY
    }

    @Value
    public static class Status {
        State state;
        String model;
        String version;

        public String getPullCommand() {
            return "ollama pull " + model;
        }

        public String getDownloadUrl() {
            return DOWNLOAD_URL;
        }
    }

    public Status status() {
        String model = props.model();
        JsonNode version = json("/api/version");
        if (version == null) {
            return new Status(installed() ? State.NOT_RUNNING : State.NOT_INSTALLED, model, null);
        }
        boolean has = false;
        JsonNode tags = json("/api/tags");
        if (tags != null) {
            for (JsonNode m : tags.path("models")) {
                String name = m.path("name").asString("");
                has |= name.equals(model) || name.equals(model + ":latest");
            }
        }
        return new Status(has ? State.READY : State.NO_MODEL, model, version.path("version").asString(""));
    }

    private JsonNode json(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(props.baseUrl() + path))
                .timeout(Duration.ofSeconds(3)).GET().build();
        try {
            HttpResponse<String> res = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? JSON.readTree(res.body()) : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** PATH나 기본 설치 위치에 ollama 실행 파일이 있는지 */
    static boolean installed() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String exe = windows ? "ollama.exe" : "ollama";
        List<Path> candidates = new ArrayList<>();
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (!dir.isBlank()) {
                    try {
                        candidates.add(Path.of(dir.strip(), exe));
                    } catch (RuntimeException ignored) {
                        // 잘못된 PATH 항목
                    }
                }
            }
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            candidates.add(Path.of(localAppData, "Programs", "Ollama", exe));
        }
        candidates.add(Path.of("/usr/local/bin/ollama"));
        candidates.add(Path.of("/Applications/Ollama.app"));
        return candidates.stream().anyMatch(Files::exists);
    }
}
