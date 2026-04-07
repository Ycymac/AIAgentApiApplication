package com.ycy.aiapplication.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
                "com.ycy.aiapplication.rag",
                "com.ycy.aiapplication.infrastructure.ai",
                "com.ycy.aiapplication.framework",
                "com.ycy.aiapplication.knowledge"
        })
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }

}
