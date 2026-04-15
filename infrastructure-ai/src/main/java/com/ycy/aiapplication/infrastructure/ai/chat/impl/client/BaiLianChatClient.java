package com.ycy.aiapplication.infrastructure.ai.chat.impl.client;

import com.google.gson.JsonObject;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelProvider;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * 百炼聊天客户端。
 * 仅覆盖与百炼平台有关的 provider 标识和 thinking 透传策略。
 */
@Service
public class BaiLianChatClient extends AbstractOpenAIStyleChatClient {

    /**
     * 初始化百炼聊天客户端，并绑定百炼独立流式线程池。
     */
    public BaiLianChatClient(
            AIModelProperties properties,
            OkHttpClient httpClient,
            @Qualifier("bailianChatStreamExecutor") Executor streamExecutor) {
        super(properties, httpClient, streamExecutor);
    }

    /**
     * 返回当前客户端对应的模型提供商标识。
     */
    @Override
    public String provider() {
        return ModelProvider.BAI_LIAN.getId();
    }

    /**
     * 按百炼接口要求仅在流式模式下开启 thinking 开关。
     */
    @Override
    protected void applyThinking(JsonObject requestBody, ChatRequest request, boolean stream) {
        requestBody.addProperty("enable_thinking", Boolean.TRUE.equals(request.getThinking()));
    }
}
