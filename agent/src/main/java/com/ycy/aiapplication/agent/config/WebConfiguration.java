package com.ycy.aiapplication.agent.config;

import com.ycy.aiapplication.agent.toolkit.interceptor.JWTInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfiguration implements WebMvcConfigurer {
    private final JWTInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/service/login",
                        "/api/user/service/sign/up",
                        //排除当前所有接口防止出现被拦截无法测试
                        "/api/agent/**"
                        );
    }
}