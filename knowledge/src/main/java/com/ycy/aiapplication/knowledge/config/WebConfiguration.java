package com.ycy.aiapplication.knowledge.config;


import com.ycy.aiapplication.user.service.toolkit.interceptor.AdminPermissionInterceptor;
import com.ycy.aiapplication.user.service.toolkit.interceptor.JWTInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfiguration implements WebMvcConfigurer {
    private final JWTInterceptor jwtInterceptor;
    private final AdminPermissionInterceptor adminPermissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .addPathPatterns("/knowledge-base/**")
                .addPathPatterns("/rag/**")
                .addPathPatterns("/conversations/**")
                .excludePathPatterns(
                        "/api/user/service/login",
                        "/api/user/service/sign/up"
                        );
        registry.addInterceptor(adminPermissionInterceptor)
                .addPathPatterns("/api/knowledge/**")
                .addPathPatterns("/knowledge-base/**");
    }
}
