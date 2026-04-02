package com.ycy.aiapplication.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.ycy.aiapplication.knowledge",
        "com.ycy.aiapplication.user.service",
        "com.ycy.aiapplication.framework"
})
public class KnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
    }

}
