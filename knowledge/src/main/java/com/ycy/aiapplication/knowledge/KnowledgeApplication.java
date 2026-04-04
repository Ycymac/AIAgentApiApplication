package com.ycy.aiapplication.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.ycy.aiapplication.knowledge",
        "com.ycy.aiapplication.user.service",
        "com.ycy.aiapplication.framework",
        "com.ycy.aiapplication.chunk",
        "com.ycy.aiapplication.parse",
        "com.ycy.aiapplication.vector",
        "com.ycy.aiapplication.infrastructure.ai"
})
public class KnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
    }

}
