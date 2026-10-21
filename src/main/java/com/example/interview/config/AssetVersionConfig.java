package com.example.interview.config;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JS·CSS 주소에 붙이는 버전(서버를 켠 시각). 재시작 뒤에도 브라우저가 예전 파일을 쓰는 일을 막는다.
 * JSP에서는 {@code ${applicationScope.assetVersion}}으로 쓴다.
 */
@Configuration
public class AssetVersionConfig {

    @Bean
    ServletContextInitializer assetVersion() {
        String version = Long.toString(System.currentTimeMillis(), 36);
        return ctx -> ctx.setAttribute("assetVersion", version);
    }
}
