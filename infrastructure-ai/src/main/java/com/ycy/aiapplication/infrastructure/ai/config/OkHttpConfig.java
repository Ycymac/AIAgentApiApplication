package com.ycy.aiapplication.infrastructure.ai.config;

import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * OkHttpClient 配置类。
 * <p>
 * 统一复用 AIModelProperties 中的 HTTP 超时配置。
 */
@Configuration
@RequiredArgsConstructor
public class OkHttpConfig {

    private final AIModelProperties properties;

    @Bean
    public OkHttpClient okHttpClient() {
        AIModelProperties.Http http = properties.getHttp();
        return new OkHttpClient.Builder()
                .connectTimeout(http.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(http.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(http.getWriteTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }
}
