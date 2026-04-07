package com.ycy.aiapplication.infrastructure.ai.chat.impl.client;

import cn.hutool.core.collection.CollUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ycy.aiapplication.framework.convention.ChatMessage;
import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.ChatClient;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCallback;
import com.ycy.aiapplication.infrastructure.ai.chat.interfaces.StreamCancellationHandle;
import com.ycy.aiapplication.infrastructure.ai.chat.toolkit.OpenAIStyleSSEParser;
import com.ycy.aiapplication.infrastructure.ai.chat.toolkit.StreamAsyncExecutor;
import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelCapability;
import com.ycy.aiapplication.infrastructure.ai.http.HttpMediaTypes;
import com.ycy.aiapplication.infrastructure.ai.http.ModelClientErrorType;
import com.ycy.aiapplication.infrastructure.ai.http.ModelClientException;
import com.ycy.aiapplication.infrastructure.ai.http.ModelURLResolver;
import com.ycy.aiapplication.infrastructure.ai.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 抽象 OpenAI 风格聊天客户端。
 * 负责复用同步请求、流式请求、消息序列化和通用响应解析逻辑。
 */
@Slf4j
public abstract class AbstractOpenAIStyleChatClient implements ChatClient {

    protected final AIModelProperties properties;
    protected final OkHttpClient httpClient;
    protected final Executor streamExecutor;
    protected final Gson gson = new Gson();

    /**
     * 初始化通用聊天客户端所需的基础依赖。
     */
    protected AbstractOpenAIStyleChatClient(AIModelProperties properties, OkHttpClient httpClient, Executor streamExecutor) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.streamExecutor = streamExecutor;
    }

    /**
     * 发起一次非流式聊天调用，并在失败时统一转换为模型客户端异常。
     */
    @Override
    public String chat(ChatRequest request, ModelTarget target) {
        Request httpRequest = buildChatRequest(request, target, false);
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String body = readBody(response.body());
                throw new ModelClientException(
                        provider() + " chat failed: HTTP " + response.code() + " - " + body,
                        classifyStatus(response.code()),
                        response.code()
                );
            }
            return extractChatContent(parseJsonBody(response.body()));
        } catch (IOException ex) {
            throw new ModelClientException(provider() + " chat failed: " + ex.getMessage(), ModelClientErrorType.NETWORK_ERROR, null, ex);
        }
    }

    /**
     * 启动一次流式聊天调用，并把真正的读取逻辑投递到专属流式线程池。
     */
    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
        Call call = httpClient.newCall(buildChatRequest(request, target, true));
        return StreamAsyncExecutor.submit(
                streamExecutor,
                call,
                callback,
                cancelled -> doStream(call, callback, cancelled, Boolean.TRUE.equals(request.getThinking()))
        );
    }

    /**
     * 消费上游 SSE 响应，并把内容片段与 thinking 片段实时转发给下游回调。
     */
    protected void doStream(Call call, StreamCallback callback, AtomicBoolean cancelled, boolean reasoningEnabled) {
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                String body = readBody(response.body());
                throw new ModelClientException(
                        provider() + " stream chat failed: HTTP " + response.code() + " - " + body,
                        classifyStatus(response.code()),
                        response.code()
                );
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new ModelClientException(provider() + " stream response is empty", ModelClientErrorType.INVALID_RESPONSE, null);
            }
            BufferedSource source = body.source();
            boolean completed = false;
            while (!cancelled.get()) {
                // 逐行读取 SSE 帧，避免把整段响应一次性读入内存。
                String line = source.readUtf8Line();
                if (line == null) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }
                // 统一按 OpenAI 风格 delta 结构解析 content / reasoning 事件。
                OpenAIStyleSSEParser.ParsedEvent event = OpenAIStyleSSEParser.parseLine(line, reasoningEnabled);
                if (event.hasReasoning()) {
                    callback.onThinking(event.getReasoning());
                }
                if (event.hasContent()) {
                    callback.onContent(event.getContent());
                }
                if (event.isCompleted()) {
                    callback.onComplete();
                    completed = true;
                    break;
                }
            }
            if (!cancelled.get() && !completed) {
                throw new ModelClientException(provider() + " stream response terminated unexpectedly", ModelClientErrorType.INVALID_RESPONSE, null);
            }
        } catch (Exception ex) {
            callback.onError(ex);
        }
    }

    /**
     * 构造同步或流式聊天 HTTP 请求。
     */
    protected Request buildChatRequest(ChatRequest request, ModelTarget target, boolean stream) {
        JsonObject requestBody = buildRequestBody(request, target, stream);
        Request.Builder builder = new Request.Builder()
                .url(resolveUrl(target))
                .post(RequestBody.create(gson.toJson(requestBody), HttpMediaTypes.JSON))
                .addHeader("Content-Type", HttpMediaTypes.JSON_UTF8_HEADER)
                .addHeader("Authorization", "Bearer " + resolveApiKey(target.provider()));
        if (stream) {
            builder.addHeader("Accept", "text/event-stream");
        }
        return builder.build();
    }

    /**
     * 组装标准 OpenAI 风格请求体，并透传常见生成参数。
     */
    protected JsonObject buildRequestBody(ChatRequest request, ModelTarget target, boolean stream) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", target.model());
        requestBody.add("messages", buildMessages(request.getMessages()));
        if (stream) {
            requestBody.addProperty("stream", true);
        }
        if (request.getTemperature() != null) {
            requestBody.addProperty("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            requestBody.addProperty("top_p", request.getTopP());
        }
        if (request.getTopK() != null) {
            requestBody.addProperty("top_k", request.getTopK());
        }
        if (request.getMaxTokens() != null) {
            requestBody.addProperty("max_tokens", request.getMaxTokens());
        }
        applyThinking(requestBody, request, stream);
        return requestBody;
    }

    /**
     * 按平台约定向请求体注入 reasoning / thinking 开关。
     */
    protected void applyThinking(JsonObject requestBody, ChatRequest request, boolean stream) {
        if (Boolean.TRUE.equals(request.getThinking())) {
            requestBody.addProperty("enable_thinking", true);
        }
    }

    /**
     * 把统一消息对象转换为上游模型接口要求的 messages 数组。
     */
    protected JsonArray buildMessages(List<ChatMessage> messages) {
        JsonArray array = new JsonArray();
        if (CollUtil.isEmpty(messages)) {
            return array;
        }
        for (ChatMessage message : messages) {
            JsonObject item = new JsonObject();
            item.addProperty("role", toRole(message.getRole()));
            item.addProperty("content", message.getContent());
            array.add(item);
        }
        return array;
    }

    /**
     * 将内部消息角色映射为 OpenAI 风格 role 字段。
     */
    protected String toRole(ChatMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    /**
     * 根据 provider 解析最终聊天接口地址。
     */
    protected String resolveUrl(ModelTarget target) {
        if ("bailian".equals(target.provider())) {
            return ModelURLResolver.resolveBaiLianUrl(properties.getProviders().getBailian(), ModelCapability.CHAT);
        }
        return ModelURLResolver.resolveSiliconFlowUrl(properties.getProviders().getSiliconflow(), ModelCapability.CHAT);
    }

    /**
     * 读取指定平台的 API Key，并在缺失时快速失败。
     */
    protected String resolveApiKey(String provider) {
        String apiKey = "bailian".equals(provider)
                ? properties.getProviders().getBailian().getApiKey()
                : properties.getProviders().getSiliconflow().getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(provider + " apiKey is missing");
        }
        return apiKey;
    }

    /**
     * 解析非流式 JSON 响应体。
     */
    protected JsonObject parseJsonBody(ResponseBody body) throws IOException {
        if (body == null) {
            throw new ModelClientException(provider() + " response is empty", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        return gson.fromJson(body.string(), JsonObject.class);
    }

    /**
     * 读取失败响应体，便于把上游错误透传到日志和异常消息中。
     */
    protected String readBody(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        return new String(body.bytes(), StandardCharsets.UTF_8);
    }

    /**
     * 从标准 choices[0].message.content 结构中提取最终回复文本。
     */
    protected String extractChatContent(JsonObject root) {
        if (root == null || !root.has("choices")) {
            throw new ModelClientException(provider() + " response missing choices", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new ModelClientException(provider() + " response choices is empty", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonObject choice = choices.get(0).getAsJsonObject();
        if (choice == null || !choice.has("message")) {
            throw new ModelClientException(provider() + " response missing message", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonObject message = choice.getAsJsonObject("message");
        if (message == null || !message.has("content") || message.get("content").isJsonNull()) {
            throw new ModelClientException(provider() + " response missing content", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        return message.get("content").getAsString();
    }

    /**
     * 根据 HTTP 状态码归类模型请求失败类型。
     */
    protected ModelClientErrorType classifyStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return ModelClientErrorType.UNAUTHORIZED;
        }
        if (statusCode == 429) {
            return ModelClientErrorType.RATE_LIMITED;
        }
        if (statusCode >= 500) {
            return ModelClientErrorType.SERVER_ERROR;
        }
        return ModelClientErrorType.CLIENT_ERROR;
    }
}
