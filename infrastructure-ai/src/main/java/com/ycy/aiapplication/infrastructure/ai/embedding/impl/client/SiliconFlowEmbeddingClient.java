package com.ycy.aiapplication.infrastructure.ai.embedding.impl.client;

import cn.hutool.core.collection.CollUtil;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.exception.ServiceException;
import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingClient;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingModelRegistry;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelCapability;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelProvider;
import com.ycy.aiapplication.infrastructure.ai.http.HttpMediaTypes;
import com.ycy.aiapplication.infrastructure.ai.http.ModelClientErrorType;
import com.ycy.aiapplication.infrastructure.ai.http.ModelClientException;
import com.ycy.aiapplication.infrastructure.ai.http.ModelURLResolver;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

    private static final int DEFAULT_BATCH_SIZE = 16;

    private final AIModelProperties properties;
    private final EmbeddingModelRegistry modelRegistry;
    private final OkHttpClient httpClient;

    private final Gson gson = new Gson();

    @Override
    public String provider() {
        return ModelProvider.SILICON_FLOW.getId();
    }

    @Override
    public boolean supports(String provider, String modelId) {
        return modelRegistry.supports(provider(), provider, modelId);
    }

    @Override
    public int dimension(String modelId) {
        Integer dimension = properties.getEmbedding().getDimension();
        return dimension == null ? 0 : dimension;
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, String modelId, Integer dimension, Integer batchSize) {
        if (CollUtil.isEmpty(texts)) {
            return Collections.emptyList();
        }

        String resolvedModel = requireModel(modelId);
        int resolvedDimension = dimension == null || dimension <= 0 ? 0 : dimension;
        int resolvedBatchSize = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        List<String> normalizedTexts = normalizeTexts(texts);

        List<List<Float>> results = new ArrayList<>(normalizedTexts.size());
        for (int i = 0; i < normalizedTexts.size(); i += resolvedBatchSize) {
            int end = Math.min(i + resolvedBatchSize, normalizedTexts.size());
            List<String> slice = normalizedTexts.subList(i, end);
            results.addAll(doEmbedOnce(slice, resolvedModel, resolvedDimension));
        }

        validateBatchResult(results, normalizedTexts.size(), resolvedDimension);
        return results;
    }

    private List<String> normalizeTexts(List<String> texts) {
        List<String> normalizedTexts = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String normalizedText = normalizeText(texts.get(i));
            if (!StringUtils.hasText(normalizedText)) {
                throw new ClientException("Embedding 输入文本不能为空，index=" + i);
            }
            normalizedTexts.add(normalizedText);
        }
        return normalizedTexts;
    }

    private List<List<Float>> doEmbedOnce(List<String> slice, String modelId, int expectedDimension) {
        AIModelProperties.SiliconFlowProvider channel = requireChannel();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelId);
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
            throw new ModelClientException("SiliconFlow embedding response missing data field", ModelClientErrorType.INVALID_RESPONSE, null);
        }

        List<List<Float>> vectors = new ArrayList<>(data.size());
        for (JsonElement element : data) {
            JsonObject item = element.getAsJsonObject();
            JsonArray embedding = item.getAsJsonArray("embedding");
            if (embedding == null) {
                throw new ModelClientException("SiliconFlow embedding response missing embedding field", ModelClientErrorType.INVALID_RESPONSE, null);
            }
            List<Float> vector = new ArrayList<>(embedding.size());
            for (JsonElement number : embedding) {
                vector.add(number.getAsFloat());
            }
            vectors.add(vector);
        }
        validateBatchResult(vectors, slice.size(), expectedDimension);
        return vectors;
    }

    private void validateBatchResult(List<List<Float>> vectors, int expectedSize, int expectedDimension) {
        if (vectors == null || vectors.size() != expectedSize) {
            throw new ServiceException("向量化结果数量不匹配，期望=" + expectedSize + "，实际=" + (vectors == null ? 0 : vectors.size()));
        }

        int runtimeDimension = 0;
        for (int i = 0; i < vectors.size(); i++) {
            List<Float> vector = vectors.get(i);
            if (CollUtil.isEmpty(vector)) {
                throw new ServiceException("向量结果为空，index=" + i);
            }
            if (expectedDimension <= 0 && runtimeDimension <= 0) {
                runtimeDimension = vector.size();
            }
            int targetDimension = expectedDimension > 0 ? expectedDimension : runtimeDimension;
            if (targetDimension > 0 && vector.size() != targetDimension) {
                throw new ServiceException("向量维度不匹配，期望=" + targetDimension + "，实际=" + vector.size() + "，index=" + i);
            }
        }
    }

    private AIModelProperties.SiliconFlowProvider requireChannel() {
        AIModelProperties.SiliconFlowProvider channel = properties.getProviders().getSiliconflow();
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

    private String requireModel(String modelId) {
        String resolvedModel = StringUtils.hasText(modelId) ? modelId : modelRegistry.getModel(provider());
        if (!StringUtils.hasText(resolvedModel)) {
            throw new ClientException("SiliconFlow embeddingModel 未配置");
        }
        return resolvedModel;
    }

    private String normalizeText(String text) {
        return text == null ? null : text.trim();
    }

    private JsonObject parseJsonBody(ResponseBody body) throws IOException {
        if (body == null) {
            throw new ModelClientException("SiliconFlow embedding response is empty", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        return JsonParser.parseString(body.string()).getAsJsonObject();
    }

    private String readBody(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        return new String(body.bytes(), StandardCharsets.UTF_8);
    }

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
