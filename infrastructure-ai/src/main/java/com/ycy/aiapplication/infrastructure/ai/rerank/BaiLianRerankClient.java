package com.ycy.aiapplication.infrastructure.ai.rerank;

import cn.hutool.core.collection.CollUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.exception.ServiceException;
import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelCapability;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelProvider;
import com.ycy.aiapplication.infrastructure.ai.http.ModelClientErrorType;
import com.ycy.aiapplication.infrastructure.ai.http.ModelClientException;
import com.ycy.aiapplication.infrastructure.ai.http.ModelURLResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaiLianRerankClient implements RerankClient {

    private final AIModelProperties properties;

    private final Gson gson = new Gson();

    private final ThreadLocal<RerankCallDiagnostics> lastCallDiagnostics = new ThreadLocal<>();

    @Override
    public String provider() {
        return ModelProvider.BAI_LIAN.getId();
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN) {
        lastCallDiagnostics.remove();
        if (!StringUtils.hasText(query)) {
            throw new ClientException("Rerank query cannot be empty");
        }
        if (CollUtil.isEmpty(candidates)) {
            return List.of();
        }

        String configuredProvider = normalize(properties.getRerank().getProvider());
        if (StringUtils.hasText(configuredProvider) && !provider().equals(configuredProvider)) {
            throw new IllegalStateException("Rerank provider is not BaiLian: " + configuredProvider);
        }

        List<RetrievedChunk> normalizedCandidates = normalizeCandidates(candidates);
        int resolvedTopN = resolveTopN(topN, normalizedCandidates.size());
        List<String> modelChain = resolveModelChain();
        RuntimeException lastException = null;

        int attemptCount = 0;
        for (String modelId : modelChain) {
            attemptCount++;
            try {
                long startAt = System.currentTimeMillis();
                List<RetrievedChunk> reranked = rerankByModel(query.trim(), normalizedCandidates, resolvedTopN, modelId);
                log.info(
                        "BaiLian rerank success, model={}, candidateCount={}, topN={}, costMs={}",
                        modelId,
                        normalizedCandidates.size(),
                        resolvedTopN,
                        System.currentTimeMillis() - startAt
                );
                lastCallDiagnostics.set(new RerankCallDiagnostics(
                        attemptCount, modelId, attemptCount > 1));
                return reranked;
            } catch (RuntimeException ex) {
                lastException = ex;
                lastCallDiagnostics.set(new RerankCallDiagnostics(
                        attemptCount, null, attemptCount > 1));
                log.warn("BaiLian rerank failed, model={}, trying next backup model", modelId, ex);
            }
        }

        if (lastException != null) {
            if (lastException instanceof ModelClientException clientException) {
                throw mapClientException(clientException);
            }
            throw lastException;
        }
        throw new IllegalStateException("No available BaiLian rerank model configured");
    }

    /**
     * Returns and clears diagnostics for the current thread's latest rerank call.
     */
    public RerankCallDiagnostics consumeLastCallDiagnostics() {
        RerankCallDiagnostics diagnostics = lastCallDiagnostics.get();
        lastCallDiagnostics.remove();
        return diagnostics;
    }

    public record RerankCallDiagnostics(int attemptCount, String selectedModel, boolean fallbackUsed) {
    }

    private List<RetrievedChunk> rerankByModel(String query, List<RetrievedChunk> candidates, int topN, String modelId) {
        AIModelProperties.BaiLianProvider channel = requireChannel();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelId);
        requestBody.put("input", buildInput(query, candidates));
        requestBody.put("parameters", buildParameters(topN));

        CurlResult result = executeCurl(ModelURLResolver.resolveBaiLianUrl(channel, ModelCapability.RERANK), channel.getApiKey(), gson.toJson(requestBody));
        JsonObject root = parseResponse(result.body(), result.httpStatus());

        if (root.has("code") && !isSuccessCode(root.get("code"))) {
            throw new ModelClientException(
                    "BaiLian rerank error: " + root.get("code").getAsString() + " - " + readMessage(root),
                    ModelClientErrorType.PROVIDER_ERROR,
                    readStatusCode(root)
            );
        }

        JsonObject output = root.getAsJsonObject("output");
        if (output == null) {
            throw new ModelClientException("BaiLian rerank response missing output field", ModelClientErrorType.INVALID_RESPONSE, result.httpStatus());
        }

        JsonArray results = output.getAsJsonArray("results");
        if (results == null) {
            throw new ModelClientException("BaiLian rerank response missing results field", ModelClientErrorType.INVALID_RESPONSE, result.httpStatus());
        }

        List<RetrievedChunk> reranked = new ArrayList<>(results.size());
        for (JsonElement element : results) {
            JsonObject item = element.getAsJsonObject();
            int index = readIndex(item, candidates.size());
            RetrievedChunk source = candidates.get(index);
            reranked.add(RetrievedChunk.builder()
                    .id(source.getId())
                    .text(readDocumentText(item, source.getText()))
                    .score(readScore(item))
                    .build());
        }

        return reranked;
    }

    private CurlResult executeCurl(String url, String apiKey, String requestJson) {
        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("--location");
        command.add("--silent");
        command.add("--show-error");
        command.add("--request");
        command.add("POST");
        command.add("--connect-timeout");
        command.add(String.valueOf(toSeconds(properties.getHttp().getConnectTimeoutMs(), 3)));
        command.add("--max-time");
        command.add(String.valueOf(toSeconds(resolveMaxTimeoutMs(), 10)));
        command.add("--header");
        command.add("Authorization: Bearer " + apiKey);
        command.add("--header");
        command.add("Content-Type: application/json");
        command.add("--data-binary");
        command.add("@-");
        command.add("--write-out");
        command.add("\n%{http_code}");
        command.add(url);

        Process process;
        try {
            process = new ProcessBuilder(command).start();
            try (OutputStream outputStream = process.getOutputStream()) {
                outputStream.write(requestJson.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
        } catch (IOException e) {
            throw new ServiceException("Failed to start curl process for BaiLian rerank: " + e.getMessage());
        }

        String stdout;
        String stderr;
        try {
            stdout = readStream(process.getInputStream());
            stderr = readStream(process.getErrorStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ModelClientException(
                        "Call BaiLian rerank by curl failed: " + stderr,
                        ModelClientErrorType.NETWORK_ERROR,
                        null
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Curl process interrupted while calling BaiLian rerank");
        } catch (IOException e) {
            throw new ServiceException("Failed to read curl response for BaiLian rerank: " + e.getMessage());
        }

        int splitIndex = stdout.lastIndexOf('\n');
        if (splitIndex < 0) {
            throw new ModelClientException("BaiLian rerank curl response missing HTTP status", ModelClientErrorType.INVALID_RESPONSE, null);
        }

        String body = stdout.substring(0, splitIndex);
        String statusText = stdout.substring(splitIndex + 1).trim();
        Integer httpStatus = parseHttpStatus(statusText);
        if (httpStatus == null) {
            throw new ModelClientException("BaiLian rerank curl HTTP status is invalid: " + statusText, ModelClientErrorType.INVALID_RESPONSE, null);
        }
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new ModelClientException(
                    "Call BaiLian rerank failed: HTTP " + httpStatus + " - " + body,
                    classifyStatus(httpStatus),
                    httpStatus
            );
        }
        return new CurlResult(body, httpStatus);
    }

    private JsonObject parseResponse(String body, Integer httpStatus) {
        if (!StringUtils.hasText(body)) {
            throw new ModelClientException("BaiLian rerank response is empty", ModelClientErrorType.INVALID_RESPONSE, httpStatus);
        }
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new ModelClientException("BaiLian rerank response is not valid JSON", ModelClientErrorType.INVALID_RESPONSE, httpStatus, ex);
        }
    }

    private List<RetrievedChunk> normalizeCandidates(List<RetrievedChunk> candidates) {
        List<RetrievedChunk> normalized = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            RetrievedChunk chunk = candidates.get(i);
            if (chunk == null || !StringUtils.hasText(chunk.getText())) {
                throw new ClientException("Rerank candidate text cannot be empty, index=" + i);
            }
            normalized.add(RetrievedChunk.builder()
                    .id(chunk.getId())
                    .text(chunk.getText().trim())
                    .score(chunk.getScore())
                    .build());
        }
        return normalized;
    }

    private Map<String, Object> buildInput(String query, List<RetrievedChunk> candidates) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        input.put("documents", candidates.stream().map(RetrievedChunk::getText).toList());
        return input;
    }

    private Map<String, Object> buildParameters(int topN) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("return_documents", true);
        parameters.put("top_n", topN);
        return parameters;
    }

    private List<String> resolveModelChain() {
        List<String> modelChain = new ArrayList<>();
        if (StringUtils.hasText(properties.getRerank().getPrimaryModel())) {
            modelChain.add(properties.getRerank().getPrimaryModel().trim());
        }
        if (properties.getRerank().getBackupModels() != null) {
            for (String model : properties.getRerank().getBackupModels()) {
                if (!StringUtils.hasText(model)) {
                    continue;
                }
                String normalizedModel = model.trim();
                if (!modelChain.contains(normalizedModel)) {
                    modelChain.add(normalizedModel);
                }
            }
        }
        return modelChain;
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
        if (!StringUtils.hasText(channel.getRerankPath())) {
            throw new IllegalStateException("BaiLian rerankPath is missing");
        }
        return channel;
    }

    private int resolveTopN(int topN, int candidateSize) {
        if (topN <= 0) {
            return candidateSize;
        }
        return Math.min(topN, candidateSize);
    }

    private long resolveMaxTimeoutMs() {
        long readTimeout = properties.getHttp().getReadTimeoutMs() == null ? 10000L : properties.getHttp().getReadTimeoutMs();
        long writeTimeout = properties.getHttp().getWriteTimeoutMs() == null ? 10000L : properties.getHttp().getWriteTimeoutMs();
        return Math.max(readTimeout, writeTimeout);
    }

    private long toSeconds(Long timeoutMs, long defaultSeconds) {
        if (timeoutMs == null || timeoutMs <= 0) {
            return defaultSeconds;
        }
        return Math.max(1L, Duration.ofMillis(timeoutMs).toSeconds());
    }

    private String readStream(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private Integer parseHttpStatus(String statusText) {
        try {
            return Integer.parseInt(statusText);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int readIndex(JsonObject item, int candidateSize) {
        if (!item.has("index") || item.get("index").isJsonNull()) {
            throw new ModelClientException("BaiLian rerank result missing index", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        int index = item.get("index").getAsInt();
        if (index < 0 || index >= candidateSize) {
            throw new ModelClientException("BaiLian rerank result index out of bounds: " + index, ModelClientErrorType.INVALID_RESPONSE, null);
        }
        return index;
    }

    private float readScore(JsonObject item) {
        if (item.has("relevance_score") && !item.get("relevance_score").isJsonNull()) {
            return item.get("relevance_score").getAsFloat();
        }
        if (item.has("score") && !item.get("score").isJsonNull()) {
            return item.get("score").getAsFloat();
        }
        throw new ModelClientException("BaiLian rerank result missing score", ModelClientErrorType.INVALID_RESPONSE, null);
    }

    private String readDocumentText(JsonObject item, String fallbackText) {
        if (!item.has("document") || item.get("document").isJsonNull()) {
            return fallbackText;
        }
        JsonElement document = item.get("document");
        if (document.isJsonPrimitive()) {
            return document.getAsString();
        }
        if (document.isJsonObject()) {
            JsonObject documentObject = document.getAsJsonObject();
            if (documentObject.has("text") && !documentObject.get("text").isJsonNull()) {
                return documentObject.get("text").getAsString();
            }
        }
        return fallbackText;
    }

    private boolean isSuccessCode(JsonElement codeElement) {
        if (codeElement == null || codeElement.isJsonNull()) {
            return true;
        }
        String code = codeElement.getAsString();
        return !StringUtils.hasText(code) || "OK".equalsIgnoreCase(code);
    }

    private Integer readStatusCode(JsonObject root) {
        if (root.has("status_code") && !root.get("status_code").isJsonNull()) {
            return root.get("status_code").getAsInt();
        }
        return null;
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

    private RuntimeException mapClientException(ModelClientException e) {
        if (e.getErrorType() == ModelClientErrorType.UNAUTHORIZED
                || e.getErrorType() == ModelClientErrorType.CLIENT_ERROR
                || e.getErrorType() == ModelClientErrorType.RATE_LIMITED) {
            return new ClientException("BaiLian rerank call failed: " + e.getMessage());
        }
        return new ServiceException("BaiLian rerank execution failed: " + e.getMessage());
    }

    private String normalize(String text) {
        return text == null ? null : text.trim().toLowerCase();
    }

    private record CurlResult(String body, Integer httpStatus) {
    }
}
