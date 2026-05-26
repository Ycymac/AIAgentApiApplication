package com.ycy.aiapplication.infrastructure.ai.embedding.impl.client;

import cn.hutool.core.collection.CollUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaiLianEmbeddingClient implements EmbeddingClient {

    private static final int DEFAULT_BATCH_SIZE = 10;

    private final AIModelProperties properties;
    private final EmbeddingModelRegistry modelRegistry;
    private final OkHttpClient httpClient;

    private final Gson gson = new Gson();

    @Override
    public String provider() {
        return ModelProvider.BAI_LIAN.getId();
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
        int expectedDimension = dimension == null || dimension <= 0 ? 0 : dimension;
        int resolvedBatchSize = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;

        List<TextHolder> normalizedTexts = normalizeTexts(texts);
        List<List<Float>> results = new ArrayList<>(Collections.nCopies(texts.size(), null));
        long startAt = System.currentTimeMillis();
        log.info("BaiLian embedding start, model={}, textCount={}, batchSize={}", resolvedModel, normalizedTexts.size(), resolvedBatchSize);

        try {
            for (int i = 0; i < normalizedTexts.size(); i += resolvedBatchSize) {
                int end = Math.min(i + resolvedBatchSize, normalizedTexts.size());
                List<TextHolder> slice = normalizedTexts.subList(i, end);
                long batchStartAt = System.currentTimeMillis();

                List<List<Float>> vectors = doEmbedOnce(slice, resolvedModel, expectedDimension);
                validateBatchResult(vectors, slice.size(), expectedDimension);

                for (int j = 0; j < slice.size(); j++) {
                    results.set(slice.get(j).index(), vectors.get(j));
                }

                log.info(
                        "BaiLian embedding batch success, model={}, batchStart={}, batchSize={}, costMs={}",
                        resolvedModel,
                        i,
                        slice.size(),
                        System.currentTimeMillis() - batchStartAt
                );
            }
        } catch (ModelClientException e) {
            log.error("BaiLian embedding http error, model={}", resolvedModel, e);
            throw mapClientException(e);
        } catch (RuntimeException e) {
            log.error("BaiLian embedding runtime error, model={}", resolvedModel, e);
            throw e;
        } catch (Exception e) {
            log.error("BaiLian embedding unexpected error, model={}", resolvedModel, e);
            throw new ServiceException("百炼向量化执行异常: " + e.getMessage());
        }

        ensureResults(results);
        log.info(
                "BaiLian embedding finished, model={}, textCount={}, costMs={}",
                resolvedModel,
                normalizedTexts.size(),
                System.currentTimeMillis() - startAt
        );
        return results;
    }

    private List<TextHolder> normalizeTexts(List<String> texts) {
        List<TextHolder> normalizedTexts = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String normalizedText = normalizeText(texts.get(i));
            if (!StringUtils.hasText(normalizedText)) {
                throw new ClientException("Embedding 输入文本不能为空，index=" + i);
            }
            normalizedTexts.add(new TextHolder(i, normalizedText));
        }
        return normalizedTexts;
    }

    private List<List<Float>> doEmbedOnce(List<TextHolder> slice, String modelId, int expectedDimension) {
        AIModelProperties.BaiLianProvider channel = requireChannel();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelId);
        requestBody.put("input", buildInput(slice));

        Map<String, Object> parameters = buildParameters(expectedDimension);
        if (!parameters.isEmpty()) {
            requestBody.put("parameters", parameters);
        }

        Request.Builder builder = new Request.Builder()
                .url(ModelURLResolver.resolveBaiLianUrl(channel, ModelCapability.EMBEDDING))
                .post(RequestBody.create(gson.toJson(requestBody), HttpMediaTypes.JSON))
                .addHeader("Content-Type", HttpMediaTypes.JSON_UTF8_HEADER)
                .addHeader("Accept", HttpMediaTypes.JSON_UTF8_HEADER)
                .addHeader("Authorization", "Bearer " + channel.getApiKey());

        JsonObject root;
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errBody = readBody(response.body());
                throw new ModelClientException(
                        "Call BaiLian embedding failed: HTTP " + response.code() + " - " + errBody,
                        classifyStatus(response.code()),
                        response.code()
                );
            }
            root = parseJsonBody(response.body());
        } catch (IOException e) {
            throw new ModelClientException(
                    "Call BaiLian embedding failed: " + e.getMessage(),
                    ModelClientErrorType.NETWORK_ERROR,
                    null,
                    e
            );
        }

        if (root.has("code") && !isSuccessCode(root.get("code"))) {
            throw new ModelClientException(
                    "BaiLian embedding error: " + root.get("code").getAsString() + " - " + readMessage(root),
                    ModelClientErrorType.PROVIDER_ERROR,
                    root.has("status_code") && !root.get("status_code").isJsonNull() ? root.get("status_code").getAsInt() : null
            );
        }

        JsonObject output = root.getAsJsonObject("output");
        if (output == null) {
            throw new ModelClientException("BaiLian embedding response missing output field", ModelClientErrorType.INVALID_RESPONSE, null);
        }

        JsonArray embeddings = output.getAsJsonArray("embeddings");
        if (embeddings == null) {
            throw new ModelClientException("BaiLian embedding response missing embeddings field", ModelClientErrorType.INVALID_RESPONSE, null);
        }

        if (embeddings.size() != slice.size()) {
            throw new ModelClientException("BaiLian embedding response size does not match request size", ModelClientErrorType.INVALID_RESPONSE, null);
        }

        List<List<Float>> vectors = new ArrayList<>(embeddings.size());
        for (JsonElement element : embeddings) {
            JsonObject item = element.getAsJsonObject();
            JsonArray embedding = item.getAsJsonArray("embedding");
            if (embedding == null) {
                throw new ModelClientException("BaiLian embedding response missing embedding field", ModelClientErrorType.INVALID_RESPONSE, null);
            }
            List<Float> vector = new ArrayList<>(embedding.size());
            for (JsonElement number : embedding) {
                vector.add(number.isJsonNull() ? 0F : number.getAsFloat());
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private Map<String, Object> buildInput(List<TextHolder> slice) {
        Map<String, Object> input = new HashMap<>();
        input.put("texts", slice.stream().map(TextHolder::text).toList());
        return input;
    }

    private Map<String, Object> buildParameters(int expectedDimension) {
        Map<String, Object> parameters = new HashMap<>();
        if (expectedDimension > 0) {
            parameters.put("dimension", expectedDimension);
        }
        return parameters;
    }

    private void ensureResults(List<List<Float>> results) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) == null) {
                throw new ServiceException("向量化结果缺失，index=" + i);
            }
        }
    }

    private RuntimeException mapClientException(ModelClientException e) {
        if (e.getErrorType() == ModelClientErrorType.UNAUTHORIZED
                || e.getErrorType() == ModelClientErrorType.CLIENT_ERROR
                || e.getErrorType() == ModelClientErrorType.RATE_LIMITED) {
            return new ClientException("百炼向量化调用失败: " + e.getMessage());
        }
        return new ServiceException("百炼向量化执行异常: " + e.getMessage());
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

    private AIModelProperties.BaiLianProvider requireChannel() {
        AIModelProperties.BaiLianProvider channel = properties.getProviders().getBailian();
        if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) {
            throw new IllegalStateException("BaiLian channel is disabled");
        }
        if (!StringUtils.hasText(channel.getApiKey())) {
            throw new IllegalStateException("BaiLian apiKey is missing");
        }
        if (!StringUtils.hasText(channel.getBaseUrl())) {
            throw new IllegalStateException("BaiLian baseUrl is missing");
        }
        if (!StringUtils.hasText(channel.getEmbeddingPath())) {
            throw new IllegalStateException("BaiLian embeddingPath is missing");
        }
        return channel;
    }

    private String requireModel(String modelId) {
        String resolvedModel = StringUtils.hasText(modelId) ? modelId : modelRegistry.getModel(provider());
        if (!StringUtils.hasText(resolvedModel)) {
            throw new ClientException("百炼 embeddingModel 未配置");
        }
        return resolvedModel;
    }

    private String normalizeText(String text) {
        return text == null ? null : text.trim();
    }

    private JsonObject parseJsonBody(ResponseBody body) throws IOException {
        if (body == null) {
            throw new ModelClientException("BaiLian embedding response is empty", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        return JsonParser.parseString(body.string()).getAsJsonObject();
    }

    private String readBody(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        return new String(body.bytes(), StandardCharsets.UTF_8);
    }

    private boolean isSuccessCode(JsonElement codeElement) {
        if (codeElement == null || codeElement.isJsonNull()) {
            return true;
        }
        String code = codeElement.getAsString();
        return !StringUtils.hasText(code) || "OK".equalsIgnoreCase(code);
    }

    private String readMessage(JsonObject root) {
        if (root.has("message") && !root.get("message").isJsonNull()) {
            return root.get("message").getAsString();
        }
        return "unknown";
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

    private record TextHolder(int index, String text) {
    }
}
