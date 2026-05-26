package com.ycy.aiapplication.knowledge.embedding;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class EmbeddingChannelConsistencyIT {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static final List<String> TEST_TEXTS = List.of(
            "Embedding channel consistency test with the same text and parameters."
    );

    @Test
    void qwen3TextEmbeddingModelsShouldMatchBaiLianTextEmbeddingV4() {
        TestSettings settings = TestSettings.from(loadApplicationYaml());
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(settings.connectTimeoutMs()))
                .readTimeout(Duration.ofMillis(settings.readTimeoutMs()))
                .writeTimeout(Duration.ofMillis(settings.writeTimeoutMs()))
                .build();

        ChannelCallResult baiLianResult = callChannel("bailian", () ->
                callBaiLianTextEmbedding(httpClient, settings, TEST_TEXTS.get(0)));
        if (!baiLianResult.success()) {
            fail("BaiLian baseline embedding call failed. " + baiLianResult.toStatus());
        }

        List<ComparisonResult> comparisonResults = new ArrayList<>();
        for (String siliconFlowModel : settings.siliconFlowModels()) {
            ChannelCallResult siliconFlowResult = callChannel("siliconflow", () ->
                    callSiliconFlowTextEmbedding(httpClient, settings, siliconFlowModel, TEST_TEXTS.get(0)));
            comparisonResults.add(ComparisonResult.from(settings, baiLianResult, siliconFlowResult, siliconFlowModel));
        }

        String report = ComparisonResult.toReport(comparisonResults);
        System.out.println(report);

        assertTrue(
                comparisonResults.stream().allMatch(ComparisonResult::success),
                () -> "At least one SiliconFlow embedding model failed or differed from BaiLian text-embedding-v4.\n"
                        + report
        );
    }

    private static List<Float> callBaiLianTextEmbedding(
            OkHttpClient httpClient,
            TestSettings settings,
            String text
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("texts", List.of(text));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("dimension", settings.dimension());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.baiLianModel());
        body.put("input", input);
        body.put("parameters", parameters);

        JsonObject root = executeJsonPost(
                httpClient,
                settings.baiLianEmbeddingUrl(),
                settings.baiLianApiKey(),
                body
        );

        JsonObject output = requireObject(root, "output", "BaiLian response missing output");
        JsonArray embeddings = requireArray(output, "embeddings", "BaiLian response missing output.embeddings");
        if (embeddings.size() != 1) {
            throw new IllegalStateException("BaiLian response embedding size mismatch: " + embeddings.size());
        }
        JsonObject item = embeddings.get(0).getAsJsonObject();
        return readFloatArray(requireArray(item, "embedding", "BaiLian response missing embedding"));
    }

    private static List<Float> callSiliconFlowTextEmbedding(
            OkHttpClient httpClient,
            TestSettings settings,
            String siliconFlowModel,
            String text
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", siliconFlowModel);
        body.put("input", text);
        body.put("encoding_format", "float");
        body.put("dimensions", settings.dimension());

        JsonObject root = executeJsonPost(
                httpClient,
                settings.siliconFlowEmbeddingUrl(),
                settings.siliconFlowApiKey(),
                body
        );

        JsonArray data = requireArray(root, "data", "SiliconFlow response missing data");
        if (data.size() != 1) {
            throw new IllegalStateException("SiliconFlow response embedding size mismatch: " + data.size());
        }
        JsonObject item = data.get(0).getAsJsonObject();
        return readFloatArray(requireArray(item, "embedding", "SiliconFlow response missing embedding"));
    }

    private static JsonObject executeJsonPost(
            OkHttpClient httpClient,
            String url,
            String apiKey,
            Map<String, Object> body
    ) {
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(GSON.toJson(body), JSON))
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseText = readBody(response.body());
            if (!response.isSuccessful()) {
                throw new IllegalStateException("HTTP " + response.code() + " from " + url + ": " + responseText);
            }
            return JsonParser.parseString(responseText).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to call " + url, e);
        }
    }

    private static String readBody(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        return new String(body.bytes(), StandardCharsets.UTF_8);
    }

    private static JsonObject requireObject(JsonObject object, String fieldName, String message) {
        if (!object.has(fieldName) || object.get(fieldName).isJsonNull() || !object.get(fieldName).isJsonObject()) {
            throw new IllegalStateException(message + ": " + object);
        }
        return object.getAsJsonObject(fieldName);
    }

    private static JsonArray requireArray(JsonObject object, String fieldName, String message) {
        if (!object.has(fieldName) || object.get(fieldName).isJsonNull() || !object.get(fieldName).isJsonArray()) {
            throw new IllegalStateException(message + ": " + object);
        }
        return object.getAsJsonArray(fieldName);
    }

    private static List<Float> readFloatArray(JsonArray jsonArray) {
        List<Float> vector = new ArrayList<>(jsonArray.size());
        for (JsonElement element : jsonArray) {
            vector.add(element.isJsonNull() ? 0F : element.getAsFloat());
        }
        return vector;
    }

    private static ChannelCallResult callChannel(String channel, VectorCall vectorCall) {
        try {
            return ChannelCallResult.success(channel, vectorCall.call());
        } catch (RuntimeException e) {
            return ChannelCallResult.failure(channel, e);
        }
    }

    private static Properties loadApplicationYaml() {
        try (InputStream inputStream = openApplicationYaml()) {
            Object loadedYaml = new Yaml().load(inputStream);
            Properties properties = new Properties();
            flattenYaml("", loadedYaml, properties);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load application.yaml", e);
        }
    }

    private static InputStream openApplicationYaml() throws IOException {
        for (Path path : List.of(
                Path.of("knowledge", "src", "main", "resources", "application.yaml"),
                Path.of("src", "main", "resources", "application.yaml")
        )) {
            if (Files.exists(path)) {
                return Files.newInputStream(path);
            }
        }
        return new ClassPathResource("application.yaml").getInputStream();
    }

    private static void flattenYaml(String prefix, Object value, Properties properties) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
                flattenYaml(key, entry.getValue(), properties);
            }
            return;
        }
        if (value != null) {
            properties.setProperty(prefix, String.valueOf(value));
        }
    }

    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl + path.substring(1);
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }

    @FunctionalInterface
    private interface VectorCall {

        List<Float> call();
    }

    private record ChannelCallResult(String channel, List<Float> vector, RuntimeException error) {

        private static ChannelCallResult success(String channel, List<Float> vector) {
            return new ChannelCallResult(channel, vector, null);
        }

        private static ChannelCallResult failure(String channel, RuntimeException error) {
            return new ChannelCallResult(channel, null, error);
        }

        private boolean success() {
            return error == null;
        }

        private String toStatus() {
            if (success()) {
                return channel + "=success, dimension=" + vector.size();
            }
            return channel + "=failed, errorType=" + error.getClass().getSimpleName()
                    + ", message=" + error.getMessage();
        }
    }

    private record TestSettings(
            String baiLianApiKey,
            String siliconFlowApiKey,
            String baiLianEmbeddingUrl,
            String siliconFlowEmbeddingUrl,
            String baiLianModel,
            List<String> siliconFlowModels,
            int dimension,
            long connectTimeoutMs,
            long readTimeoutMs,
            long writeTimeoutMs
    ) {

        private static TestSettings from(Properties yaml) {
            String baiLianApiKey = requiredSecret(
                    setting("embedding.consistency.bailian-api-key", "BAILIAN_API_KEY",
                            yaml.getProperty("ai.providers.bailian.api-key"))
            );
            String siliconFlowApiKey = requiredSecret(
                    setting("embedding.consistency.siliconflow-api-key", "SILICONFLOW_API_KEY",
                            yaml.getProperty("ai.providers.siliconflow.api-key"))
            );

            String baiLianBaseUrl = setting("embedding.consistency.bailian-base-url", "BAILIAN_BASE_URL",
                    yaml.getProperty("ai.providers.bailian.base-url", "https://dashscope.aliyuncs.com"));
            String siliconFlowBaseUrl = setting("embedding.consistency.siliconflow-base-url", "SILICONFLOW_BASE_URL",
                    yaml.getProperty("ai.providers.siliconflow.base-url", "https://api.siliconflow.cn"));

            String baiLianPath = setting("embedding.consistency.bailian-embedding-path", "BAILIAN_EMBEDDING_PATH",
                    yaml.getProperty("ai.providers.bailian.embedding-path",
                            "/api/v1/services/embeddings/text-embedding/text-embedding"));
            String siliconFlowPath = setting("embedding.consistency.siliconflow-embedding-path",
                    "SILICONFLOW_EMBEDDING_PATH", "/v1/embeddings");

            return new TestSettings(
                    baiLianApiKey,
                    siliconFlowApiKey,
                    joinUrl(baiLianBaseUrl, baiLianPath),
                    joinUrl(siliconFlowBaseUrl, siliconFlowPath),
                    setting("embedding.consistency.bailian-model", "BAILIAN_EMBEDDING_MODEL",
                            "text-embedding-v4"),
                    listSetting("embedding.consistency.siliconflow-models", "SILICONFLOW_EMBEDDING_MODELS",
                            "Qwen/Qwen3-Embedding-0.6B,Qwen/Qwen3-Embedding-4B,Qwen/Qwen3-Embedding-8B"),
                    intSetting("embedding.consistency.dimension", "EMBEDDING_DIMENSION",
                            Integer.parseInt(yaml.getProperty("ai.embedding.dimension", "1024"))),
                    longSetting("embedding.consistency.connect-timeout-ms", "EMBEDDING_CONNECT_TIMEOUT_MS", 2500L),
                    longSetting("embedding.consistency.read-timeout-ms", "EMBEDDING_READ_TIMEOUT_MS", 30000L),
                    longSetting("embedding.consistency.write-timeout-ms", "EMBEDDING_WRITE_TIMEOUT_MS", 30000L)
            );
        }

        private static String requiredSecret(String value) {
            Assumptions.assumeTrue(StringUtils.hasText(value), "Embedding API key is not configured");
            return value;
        }

        private static String setting(String systemProperty, String environmentVariable, String defaultValue) {
            String propertyValue = System.getProperty(systemProperty);
            if (StringUtils.hasText(propertyValue)) {
                return propertyValue;
            }
            String environmentValue = System.getenv(environmentVariable);
            if (StringUtils.hasText(environmentValue)) {
                return environmentValue;
            }
            return defaultValue;
        }

        private static List<String> listSetting(String systemProperty, String environmentVariable, String defaultValue) {
            String value = setting(systemProperty, environmentVariable, defaultValue);
            return java.util.Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }

        private static int intSetting(String systemProperty, String environmentVariable, int defaultValue) {
            return Integer.parseInt(setting(systemProperty, environmentVariable, String.valueOf(defaultValue)));
        }

        private static long longSetting(String systemProperty, String environmentVariable, long defaultValue) {
            return Long.parseLong(setting(systemProperty, environmentVariable, String.valueOf(defaultValue)));
        }
    }

    private record ComparisonResult(
            String baiLianModel,
            String siliconFlowModel,
            int dimension,
            ChannelCallResult siliconFlowResult,
            ComparisonMetrics metrics
    ) {

        private static ComparisonResult from(
                TestSettings settings,
                ChannelCallResult baiLianResult,
                ChannelCallResult siliconFlowResult,
                String siliconFlowModel
        ) {
            ComparisonMetrics metrics = siliconFlowResult.success()
                    ? ComparisonMetrics.from(baiLianResult.vector(), siliconFlowResult.vector())
                    : null;
            return new ComparisonResult(
                    settings.baiLianModel(),
                    siliconFlowModel,
                    settings.dimension(),
                    siliconFlowResult,
                    metrics
            );
        }

        private boolean success() {
            return siliconFlowResult.success() && metrics != null && metrics.exactlyEqual();
        }

        private String toLine() {
            if (!siliconFlowResult.success()) {
                return "bailianModel=" + baiLianModel
                        + ", siliconFlowModel=" + siliconFlowModel
                        + ", dimensionParam=" + dimension
                        + ", status=call_failed"
                        + ", " + siliconFlowResult.toStatus();
            }
            return "bailianModel=" + baiLianModel
                    + ", siliconFlowModel=" + siliconFlowModel
                    + ", dimensionParam=" + dimension
                    + ", status=" + (success() ? "matched" : "not_matched")
                    + ", " + metrics.toShortReport();
        }

        private static String toReport(List<ComparisonResult> results) {
            StringBuilder report = new StringBuilder("Embedding channel consistency results:");
            for (ComparisonResult result : results) {
                report.append(System.lineSeparator()).append(result.toLine());
            }
            report.append(System.lineSeparator())
                    .append("textCount=")
                    .append(TEST_TEXTS.size())
                    .append(", text=\"")
                    .append(TEST_TEXTS.get(0))
                    .append("\"");
            return report.toString();
        }
    }

    private record ComparisonMetrics(
            int baiLianDimension,
            int siliconFlowDimension,
            boolean exactlyEqual,
            int differingValueCount,
            int maxAbsDiffIndex,
            double maxAbsDiff,
            double cosineSimilarity
    ) {

        private static ComparisonMetrics from(List<Float> left, List<Float> right) {
            int sharedSize = Math.min(left.size(), right.size());
            int differingValueCount = Math.abs(left.size() - right.size());
            int maxAbsDiffIndex = -1;
            double maxAbsDiff = 0D;
            double dot = 0D;
            double leftNorm = 0D;
            double rightNorm = 0D;

            for (int i = 0; i < sharedSize; i++) {
                float leftValue = left.get(i);
                float rightValue = right.get(i);
                double absDiff = Math.abs(leftValue - rightValue);
                if (Float.compare(leftValue, rightValue) != 0) {
                    differingValueCount++;
                }
                if (absDiff > maxAbsDiff) {
                    maxAbsDiff = absDiff;
                    maxAbsDiffIndex = i;
                }
                dot += (double) leftValue * rightValue;
                leftNorm += (double) leftValue * leftValue;
                rightNorm += (double) rightValue * rightValue;
            }

            double cosineSimilarity = leftNorm == 0D || rightNorm == 0D
                    ? 0D
                    : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));

            return new ComparisonMetrics(
                    left.size(),
                    right.size(),
                    left.equals(right),
                    differingValueCount,
                    maxAbsDiffIndex,
                    maxAbsDiff,
                    cosineSimilarity
            );
        }

        private String toShortReport() {
            return "baiLianDimension=" + baiLianDimension
                    + ", siliconFlowDimension=" + siliconFlowDimension
                    + ", exactlyEqual=" + exactlyEqual
                    + ", differingValueCount=" + differingValueCount
                    + ", maxAbsDiff=" + maxAbsDiff
                    + ", maxAbsDiffIndex=" + maxAbsDiffIndex
                    + ", cosineSimilarity=" + cosineSimilarity;
        }
    }
}
