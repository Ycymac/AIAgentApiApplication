package com.ycy.aiapplication.infrastructure.ai.chat.impl.client;

import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelProvider;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * SiliconFlow 聊天客户端。
 * 复用抽象父类的通用 OpenAI 风格协议实现。
 */
@Service
public class SiliconFlowChatClient extends AbstractOpenAIStyleChatClient {

    /**
     * 初始化 SiliconFlow 聊天客户端，并绑定独立的流式线程池。
     */
    public SiliconFlowChatClient(
            AIModelProperties properties,
            OkHttpClient httpClient,
            @Qualifier("siliconFlowChatStreamExecutor") Executor streamExecutor) {
        super(properties, httpClient, streamExecutor);
    }

    /**
     * 返回当前客户端对应的模型提供商标识。
     */
    @Override
    public String provider() {
        return ModelProvider.SILICON_FLOW.getId();
    }
}
