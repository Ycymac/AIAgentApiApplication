package com.ycy.aiapplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
                "com.ycy.aiapplication.config",
                "com.ycy.aiapplication.controller",
                "com.ycy.aiapplication.service",
                "com.ycy.aiapplication.user.service.toolkit"
        }
)
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }

}
