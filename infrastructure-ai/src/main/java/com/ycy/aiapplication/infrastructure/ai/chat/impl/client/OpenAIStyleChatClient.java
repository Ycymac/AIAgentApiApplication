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
import com.ycy.aiapplication.infrastructure.ai.enums.ModelProvider;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 统一的 OpenAI 风格聊天客户端。
 * <p>
 * 请求协议、SSE 解析和响应解析保持一致，provider 只用于解析平台 URL、API Key 和少量参数兼容。
 */
@Slf4j
@Service
public class OpenAIStyleChatClient implements ChatClient {

    private final AIModelProperties properties;
    private final OkHttpClient httpClient;
    private final Executor streamExecutor;
    private final Gson gson = new Gson();

    public OpenAIStyleChatClient(
            AIModelProperties properties,
            OkHttpClient httpClient,
            @Qualifier("chatStreamExecutor") Executor streamExecutor) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.streamExecutor = streamExecutor;
    }

    @Override
    public String chat(ChatRequest request, ModelTarget target) {
        Request httpRequest = buildChatRequest(request, target, false);
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String body = readBody(response.body());
                throw new ModelClientException(
                        target.provider() + " chat failed: HTTP " + response.code() + " - " + body,
                        classifyStatus(response.code()),
                        response.code()
                );
            }
            return extractChatContent(parseJsonBody(response.body(), target));
        } catch (IOException ex) {
            throw new ModelClientException(target.provider() + " chat failed: " + ex.getMessage(), ModelClientErrorType.NETWORK_ERROR, null, ex);
        }
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
        Call call = httpClient.newCall(buildChatRequest(request, target, true));
        return StreamAsyncExecutor.submit(
                streamExecutor,
                call,
                callback,
                cancelled -> doStream(call, callback, cancelled, Boolean.TRUE.equals(request.getThinking()), target)
        );
    }

    private void doStream(
            Call call,
            StreamCallback callback,
            AtomicBoolean cancelled,
            boolean reasoningEnabled,
            ModelTarget target) {
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                String body = readBody(response.body());
                throw new ModelClientException(
                        target.provider() + " stream chat failed: HTTP " + response.code() + " - " + body,
                        classifyStatus(response.code()),
                        response.code()
                );
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new ModelClientException(target.provider() + " stream response is empty", ModelClientErrorType.INVALID_RESPONSE, null);
            }

            BufferedSource source = body.source();
            boolean completed = false;
            while (!cancelled.get()) {
                String line = source.readUtf8Line();
                if (line == null) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }

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
                throw new ModelClientException(target.provider() + " stream response terminated unexpectedly", ModelClientErrorType.INVALID_RESPONSE, null);
            }
        } catch (Exception ex) {
            callback.onError(ex);
        }
    }

    private Request buildChatRequest(ChatRequest request, ModelTarget target, boolean stream) {
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

    private JsonObject buildRequestBody(ChatRequest request, ModelTarget target, boolean stream) {
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
        applyThinking(requestBody, request, target);
        return requestBody;
    }

    private void applyThinking(JsonObject requestBody, ChatRequest request, ModelTarget target) {
        boolean thinking = Boolean.TRUE.equals(request.getThinking());
        if (ModelProvider.BAI_LIAN.matches(target.provider())) {
            requestBody.addProperty("enable_thinking", thinking);
            return;
        }
        if (thinking) {
            requestBody.addProperty("enable_thinking", true);
        }
    }

    private JsonArray buildMessages(List<ChatMessage> messages) {
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

    private String toRole(ChatMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private String resolveUrl(ModelTarget target) {
        if (ModelProvider.BAI_LIAN.matches(target.provider())) {
            return ModelURLResolver.resolveBaiLianUrl(properties.getProviders().getBailian(), ModelCapability.CHAT);
        }
        if (ModelProvider.SILICON_FLOW.matches(target.provider())) {
            return ModelURLResolver.resolveSiliconFlowUrl(properties.getProviders().getSiliconflow(), ModelCapability.CHAT);
        }
        throw new IllegalStateException("Unsupported chat provider: " + target.provider());
    }

    private String resolveApiKey(String provider) {
        String apiKey;
        if (ModelProvider.BAI_LIAN.matches(provider)) {
            apiKey = properties.getProviders().getBailian().getApiKey();
        } else if (ModelProvider.SILICON_FLOW.matches(provider)) {
            apiKey = properties.getProviders().getSiliconflow().getApiKey();
        } else {
            throw new IllegalStateException("Unsupported chat provider: " + provider);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(provider + " apiKey is missing");
        }
        return apiKey;
    }

    private JsonObject parseJsonBody(ResponseBody body, ModelTarget target) throws IOException {
        if (body == null) {
            throw new ModelClientException(target.provider() + " response is empty", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        return gson.fromJson(body.string(), JsonObject.class);
    }

    private String readBody(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        return new String(body.bytes(), StandardCharsets.UTF_8);
    }

    private String extractChatContent(JsonObject root) {
        if (root == null || !root.has("choices")) {
            throw new ModelClientException("chat response missing choices", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new ModelClientException("chat response choices is empty", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonObject choice = choices.get(0).getAsJsonObject();
        if (choice == null || !choice.has("message")) {
            throw new ModelClientException("chat response missing message", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonObject message = choice.getAsJsonObject("message");
        if (message == null || !message.has("content") || message.get("content").isJsonNull()) {
            throw new ModelClientException("chat response missing content", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        return message.get("content").getAsString();
    }

    private ModelClientErrorType classifyStatus(int statusCode) {
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
