package com.ycy.aiapplication.framework.config;

import com.ycy.aiapplication.framework.web.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;

public class WebAutoConfiguration {
    @Bean
    public GlobalExceptionHandler globalExceptionHandler(){return new GlobalExceptionHandler();}
}