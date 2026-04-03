package com.ycy.aiapplication.infrastructure.ai.embedding.impl.client;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.dashscope.embeddings.MultiModalEmbedding;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemBase;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemText;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingParam;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResult;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResultItem;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.exception.ServiceException;
import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.embedding.EmbeddingClient;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于阿里百炼官方 SDK 的向量化客户端。
 * <p>
 * 当前实现关注三类能力：
 * 1. 批量切片，避免一次性提交过大请求
 * 2. 输入清洗与结果校验，保证返回结构稳定
 * 3. 关键日志记录，便于排查线上问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QwenSDKEmbeddingClient implements EmbeddingClient {

    private final AIModelProperties properties;

    /**
     * 返回当前客户端对应的提供商标识。
     *
     * @return 提供商 ID
     */
    @Override
    public String provider() {
        return ModelProvider.BAI_LIAN.getId();
    }

    /**
     * 对单条文本执行向量化。
     *
     * @param text 文本内容
     * @return 向量结果
     */
    @Override
    public List<Float> embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /**
     * 批量执行文本向量化。
     * <p>
     * 处理步骤：
     * 1. 过滤空文本并保留原有顺序映射
     * 2. 按 batchSize 分批调用百炼 SDK
     * 3. 校验返回结果数量和向量维度
     * 4. 将结果按原始输入顺序组装返回
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (CollUtil.isEmpty(texts)) {
            return Collections.emptyList();
        }

        String model = requireModel();
        String apiKey = requireApiKey();
        int batchSize = resolveBatchSize();
        int expectedDimension = resolveExpectedDimension();

        // 先进行文本清洗，并保留原始下标，保证最终结果能按调用顺序回填。
        List<TextHolder> normalizedTexts = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String normalizedText = normalizeText(texts.get(i));
            if (!StringUtils.hasText(normalizedText)) {
                throw new ClientException("Embedding 输入文本不能为空，index=" + i);
            }
            normalizedTexts.add(new TextHolder(i, normalizedText));
        }

        List<List<Float>> results = new ArrayList<>(Collections.nCopies(texts.size(), null));
        long startAt = System.currentTimeMillis();
        log.info("BaiLian embedding start, model={}, textCount={}, batchSize={}", model, normalizedTexts.size(), batchSize);

        try {
            for (int i = 0; i < normalizedTexts.size(); i += batchSize) {
                int end = Math.min(i + batchSize, normalizedTexts.size());
                List<TextHolder> slice = normalizedTexts.subList(i, end);
                long batchStartAt = System.currentTimeMillis();

                List<List<Float>> vectors = doEmbedOnce(slice, model, apiKey);
                validateBatchResult(vectors, slice.size(), expectedDimension);

                for (int j = 0; j < slice.size(); j++) {
                    results.set(slice.get(j).index(), vectors.get(j));
                }

                log.info(
                        "BaiLian embedding batch success, model={}, batchStart={}, batchSize={}, costMs={}",
                        model,
                        i,
                        slice.size(),
                        System.currentTimeMillis() - batchStartAt
                );
            }
        } catch (UploadFileException | NoApiKeyException e) {
            log.error("BaiLian embedding sdk error, model={}", model, e);
            throw new ClientException("百炼向量化调用失败: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("BaiLian embedding runtime error, model={}", model, e);
            throw e;
        } catch (Exception e) {
            log.error("BaiLian embedding unexpected error, model={}", model, e);
            throw new ServiceException("百炼向量化执行异常: " + e.getMessage());
        }

        // 这里再做一次整体校验，防止中间某批次未正确回填。
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) == null) {
                throw new ServiceException("向量化结果缺失，index=" + i);
            }
        }

        log.info(
                "BaiLian embedding finished, model={}, textCount={}, costMs={}",
                model,
                normalizedTexts.size(),
                System.currentTimeMillis() - startAt
        );
        return results;
    }

    /**
     * 单批次调用百炼 SDK。
     *
     * @param slice  单批次文本
     * @param model  模型名
     * @param apiKey 访问密钥
     * @return 单批次向量结果
     * @throws NoApiKeyException   百炼 SDK 异常
     * @throws UploadFileException 百炼 SDK 异常
     */
    private List<List<Float>> doEmbedOnce(List<TextHolder> slice, String model, String apiKey)
            throws NoApiKeyException, UploadFileException {
        List<MultiModalEmbeddingItemBase> textItems = slice.stream()
                .map(TextHolder::text)
                .map(MultiModalEmbeddingItemText::new)
                .collect(Collectors.toList());

        MultiModalEmbeddingParam param = MultiModalEmbeddingParam.builder()
                .model(model)
                .contents(textItems)
                .apiKey(apiKey)
                .build();

        MultiModalEmbedding embedding = new MultiModalEmbedding();
        MultiModalEmbeddingResult result = embedding.call(param);
        if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
            throw new ServiceException("百炼向量化返回为空");
        }

        List<MultiModalEmbeddingResultItem> embeddingItems = result.getOutput().getEmbeddings();
        if (embeddingItems.size() != slice.size()) {
            throw new ServiceException("向量化结果数量不匹配，期望=" + slice.size() + "，实际=" + embeddingItems.size());
        }

        List<List<Float>> vectors = new ArrayList<>(embeddingItems.size());
        for (MultiModalEmbeddingResultItem item : embeddingItems) {
            if (item == null || item.getEmbedding() == null) {
                throw new ServiceException("百炼返回的 embedding 结果为空");
            }
            List<Float> vector = item.getEmbedding().stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());
            vectors.add(vector);
        }
        return vectors;
    }

    /**
     * 校验单批次向量结果。
     *
     * @param vectors            向量结果
     * @param expectedSize       期望数量
     * @param expectedDimension  期望维度，0 表示不强制
     */
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

            // 如果配置里没有明确维度，则退化为使用第一条向量的长度作为运行时校验基准。
            if (expectedDimension <= 0 && runtimeDimension <= 0) {
                runtimeDimension = vector.size();
            }

            int targetDimension = expectedDimension > 0 ? expectedDimension : runtimeDimension;
            if (targetDimension > 0 && vector.size() != targetDimension) {
                throw new ServiceException("向量维度不匹配，期望=" + targetDimension + "，实际=" + vector.size() + "，index=" + i);
            }
        }
    }

    /**
     * 规范化输入文本。
     *
     * @param text 原始文本
     * @return 去首尾空白后的文本
     */
    private String normalizeText(String text) {
        return text == null ? null : text.trim();
    }

    /**
     * 获取并校验模型名。
     *
     * @return 模型名
     */
    private String requireModel() {
        String model = properties.getBaiLian() == null ? null : properties.getBaiLian().getEmbeddingModel();
        if (!StringUtils.hasText(model)) {
            throw new ClientException("百炼 embeddingModel 未配置");
        }
        return model;
    }

    /**
     * 获取并校验 API Key。
     *
     * @return API Key
     */
    private String requireApiKey() {
        String apiKey = properties.getBaiLian() == null ? null : properties.getBaiLian().getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new ClientException("百炼 apiKey 未配置");
        }
        return apiKey;
    }

    /**
     * 解析批量大小。
     * <p>
     * 如果统一配置未设置或设置非法值，则默认按 16 处理。
     *
     * @return 批量大小
     */
    private int resolveBatchSize() {
        Integer batchSize = properties.getEmbedding() == null ? null : properties.getEmbedding().getBatchSize();
        return batchSize == null || batchSize <= 0 ? 16 : batchSize;
    }

    /**
     * 解析期望维度。
     * <p>
     * 如果统一配置未设置，则返回 0，表示运行时用第一条向量长度做兜底校验。
     *
     * @return 期望维度
     */
    private int resolveExpectedDimension() {
        Integer dimension = properties.getEmbedding() == null ? null : properties.getEmbedding().getDimension();
        return dimension == null || dimension <= 0 ? 0 : dimension;
    }

    /**
     * 文本与原始下标的包装对象。
     *
     * @param index 原始下标
     * @param text  文本内容
     */
    private record TextHolder(int index, String text) {
    }
}
