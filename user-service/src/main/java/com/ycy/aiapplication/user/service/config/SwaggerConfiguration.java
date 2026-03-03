package com.ycy.aiapplication.user.service.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 设置文档 API Swagger 配置信息，为了让 <a href="http://127.0.0.1:{server.port}{server.servlet.context-path}/doc.html" /> 中的信息看着更饱满
 * 简而言之就是丰富api文档
 */
@Slf4j
@Configuration
public class SwaggerConfiguration implements ApplicationRunner {
    //两个模板
    //配置文件当中寻找并进行替换
    //server.port进行寻找，找不到填充默认值8080
    @Value("${server.port:8080}")
    private String serverPort;
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /**
     * 自定义openAPI个性化信息
     */
    @Bean
    public OpenAPI openAPI(){
        return new OpenAPI()
                .info(new Info() // 基本信息配置
                        .title("AIApplication-用户服务模块") // 标题
                        .description("用户登录、注册、调用服务、查看报告等") // 描述 Api 接口文档的基本信息
                        .version("v1.0.0") // 版本

                );
    }

    /**
     * 方便大家启动项目后可以直接点击链接跳转，而不用自己到浏览器输入路径
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        //通过日志连接进行跳转
        log.info("API Document: http://127.0.0.1:{}{}/doc.html", serverPort, contextPath);

    }
}