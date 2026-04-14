package com.ycy.aiapplication.rag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
                "com.ycy.aiapplication.rag",
                "com.ycy.aiapplication.infrastructure.ai",
                "com.ycy.aiapplication.knowledge",
                "com.ycy.aiapplication.user.service.dao.mapper",
                "com.ycy.aiapplication.user.service.dao.entity",
                "com.ycy.aiapplication.user.service.toolkit"
        })
@MapperScan("com.ycy.aiapplication.user.service.dao.mapper")
@MapperScan("com.ycy.aiapplication.rag.dao.mapper")
@MapperScan("com.ycy.aiapplication.knowledge.dao.mapper")
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }

}
