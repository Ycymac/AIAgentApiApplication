package com.ycy.aiapplication.rag.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties("rag.core.config")
public class RAGCoreConfig {
    //是否允许使用mcp
    private  boolean mcpEnabled=false;
}
