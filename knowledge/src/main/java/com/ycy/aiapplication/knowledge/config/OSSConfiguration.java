package com.ycy.aiapplication.knowledge.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix="aliyun.oss")
public class OSSConfiguration {

    private String endPoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

    @Bean
    public OSS ossClient(){
        return new OSSClientBuilder().build(endPoint,accessKeyId,accessKeySecret);
    }
}
