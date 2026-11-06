package com.example.interview.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.interview.config.AiProperties;
import com.example.interview.service.BusinessException;

class ApiKeyStoreTest {

    @TempDir
    Path dir;

    private ApiKeyStore store(String key, Path file) {
        AiProperties props = new AiProperties(key, "https://example.invalid/v1", "m", null, null, null, null, null, null, null, null, null);
        return new ApiKeyStore(props, file) {
            @Override
            void check(String k) {
                // 외부 호출 없이
            }
        };
    }

    @Test
    void replacesOnlyTheKeyLine() throws Exception {
        Path file = dir.resolve("config/application-local.properties");
        Files.createDirectories(file.getParent());
        Files.write(file, List.of("spring.datasource.password=pw", "ai.api-key=old"), StandardCharsets.UTF_8);
        ApiKeyStore s = store("old", file);

        s.save("  sk-new-key-0123456789abcdef  ");

        assertThat(Files.readAllLines(file, StandardCharsets.UTF_8))
                .containsExactly("spring.datasource.password=pw", "ai.api-key=sk-new-key-0123456789abcdef");
        assertThat(s.get()).isEqualTo("sk-new-key-0123456789abcdef");
        assertThat(s.getMasked()).isEqualTo("sk-new-…cdef");
    }

    @Test
    void createsFileAndRejectsBadShape() throws Exception {
        Path file = dir.resolve("config/application-local.properties");
        ApiKeyStore s = store("", file);
        assertThat(s.isSet()).isFalse();

        assertThatThrownBy(() -> s.save("키 아님")).isInstanceOf(BusinessException.class);
        assertThat(Files.exists(file)).isFalse();

        s.save("sk-proj-abcdefghijklmnopqrstuvwxyz");
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("ai.api-key=sk-proj-abcdefghijklmnopqrstuvwxyz");
        assertThat(s.isSet()).isTrue();
    }
}
