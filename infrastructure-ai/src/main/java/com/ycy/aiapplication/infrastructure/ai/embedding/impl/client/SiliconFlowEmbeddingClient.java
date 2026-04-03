package com.ycy.aiapplication.infrastructure.ai.embedding.impl.client;

import cn.hutool.core.collection.CollUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingClient;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelCapability;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelProvider;
import com.ycy.aiapplication.infrastructure.ai.http.HttpMediaTypes;
import com.ycy.aiapplication.infrastructure.ai.http.ModelClientErrorType;
import com.ycy.aiapplication.infrastructure.ai.http.ModelClientException;
import com.ycy.aiapplication.infrastructure.ai.http.ModelURLResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowEmbeddingClient implements EmbeddingClient {

    private static final int DEFAULT_MAX_BATCH = 32;

    private final AIModelProperties properties;
    private final OkHttpClient httpClient;

    private final Gson gson = new Gson();

    @Override
    public String provider() {
        return ModelProvider.SILICON_FLOW.getId();
    }

    @Override
    public List<Float> embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (CollUtil.isEmpty(texts)) {
            return Collections.emptyList();
        }

        List<List<Float>> results = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += DEFAULT_MAX_BATCH) {
            int end = Math.min(i + DEFAULT_MAX_BATCH, texts.size());
            List<String> slice = texts.subList(i, end);
            results.addAll(doEmbedOnce(slice));
        }

        if (results.size() != texts.size()) {
            throw new ModelClientException(
                    "Embedding result size does not match request size",
                    ModelClientErrorType.INVALID_RESPONSE,
                    null
            );
        }
        return results;
    }

    /**
     * 单次调用 SiliconFlow Embedding 接口。
     *
     * @param slice 单批次文本
     * @return 向量结果
     */
    private List<List<Float>> doEmbedOnce(List<String> slice) {
        AIModelProperties.SiliconFlow channel = requireChannel();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", requireModel());
        requestBody.put("input", slice);
        requestBody.put("encoding_format", "float");

        Request request = new Request.Builder()
                .url(ModelURLResolver.resolveSiliconFlowUrl(channel, ModelCapability.EMBEDDING))
                .post(RequestBody.create(gson.toJson(requestBody), HttpMediaTypes.JSON))
                .addHeader("Content-Type", HttpMediaTypes.JSON_UTF8_HEADER)
                .addHeader("Authorization", "Bearer " + channel.getApiKey())
                .build();

        JsonObject root;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = readBody(response.body());
                log.error("SiliconFlow embedding http error, status={}, body={}", response.code(), errBody);
                throw new ModelClientException(
                        "Call SiliconFlow embedding failed: HTTP " + response.code() + " - " + errBody,
                        classifyStatus(response.code()),
                        response.code()
                );
            }
            root = parseJsonBody(response.body());
        } catch (IOException e) {
            throw new ModelClientException(
                    "Call SiliconFlow embedding failed: " + e.getMessage(),
                    ModelClientErrorType.NETWORK_ERROR,
                    null,
                    e
            );
        }

        if (root.has("error") && root.get("error").isJsonObject()) {
            JsonObject err = root.getAsJsonObject("error");
            String code = err.has("code") ? err.get("code").getAsString() : "unknown";
            String msg = err.has("message") ? err.get("message").getAsString() : "unknown";
            throw new ModelClientException(
                    "SiliconFlow embedding error: " + code + " - " + msg,
                    ModelClientErrorType.PROVIDER_ERROR,
                    null
            );
        }

        JsonArray data = root.getAsJsonArray("data");
        if (data == null) {
            throw new ModelClientException(
                    "SiliconFlow embedding response missing data field",
                    ModelClientErrorType.INVALID_RESPONSE,
                    null
            );
        }

        List<List<Float>> vectors = new ArrayList<>(data.size());
        for (JsonElement element : data) {
            JsonObject item = element.getAsJsonObject();
            JsonArray embedding = item.getAsJsonArray("embedding");
            if (embedding == null) {
                throw new ModelClientException(
                        "SiliconFlow embedding response missing embedding field",
                        ModelClientErrorType.INVALID_RESPONSE,
                        null
                );
            }
            List<Float> vector = new ArrayList<>(embedding.size());
            for (JsonElement number : embedding) {
                vector.add(number.getAsFloat());
            }
            vectors.add(vector);
        }
        return vectors;
    }

    /**
     * 获取并校验 SiliconFlow 通道配置。
     *
     * @return SiliconFlow 配置
     */
    private AIModelProperties.SiliconFlow requireChannel() {
        AIModelProperties.SiliconFlow channel = properties.getSiliconFlow();
        if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) {
            throw new IllegalStateException("SiliconFlow channel is disabled");
        }
        if (!StringUtils.hasText(channel.getApiKey())) {
            throw new IllegalStateException("SiliconFlow apiKey is missing");
        }
        if (!StringUtils.hasText(channel.getBaseUrl())) {
            throw new IllegalStateException("SiliconFlow baseUrl is missing");
        }
        return channel;
    }

    /**
     * 获取并校验 SiliconFlow embedding 模型名。
     *
     * @return 模型名
     */
    private String requireModel() {
        String model = properties.getSiliconFlow() == null ? null : properties.getSiliconFlow().getEmbeddingModel();
        if (!StringUtils.hasText(model)) {
            throw new IllegalStateException("SiliconFlow embedding model is missing");
        }
        return model;
    }

    /**
     * 解析响应体 JSON。
     *
     * @param body 响应体
     * @return JSON 对象
     * @throws IOException IO 异常
     */
    private JsonObject parseJsonBody(ResponseBody body) throws IOException {
        if (body == null) {
            throw new ModelClientException("SiliconFlow embedding response is empty", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        String content = body.string();
        return JsonParser.parseString(content).getAsJsonObject();
    }

    /**
     * 读取响应体字符串。
     *
     * @param body 响应体
     * @return 文本内容
     * @throws IOException IO 异常
     */
    private String readBody(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        return new String(body.bytes(), StandardCharsets.UTF_8);
    }

    /**
     * 根据 HTTP 状态码映射错误类型。
     *
     * @param status HTTP 状态码
     * @return 错误类型
     */
    private ModelClientErrorType classifyStatus(int status) {
        if (status == 401 || status == 403) {
            return ModelClientErrorType.UNAUTHORIZED;
        }
        if (status == 429) {
            return ModelClientErrorType.RATE_LIMITED;
        }
        if (status >= 500) {
            return ModelClientErrorType.SERVER_ERROR;
        }
        return ModelClientErrorType.CLIENT_ERROR;
    }
}
