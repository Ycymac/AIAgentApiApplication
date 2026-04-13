package com.ycy.aiapplication.rag.core.retrieve.channel.retriver;

import com.ycy.aiapplication.framework.convention.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 并行检索模板类。
 * <p>
 * 整体职责：
 * 1. 统一封装“针对一组目标并行执行检索任务”的通用流程。
 * 2. 负责异步任务提交、结果汇总、失败兜底与统计日志输出。
 * 3. 子类只需关心单个目标的具体检索实现以及日志标识。
 *
 * @param <T> 并行检索目标类型，例如 collection 名称。
 */
@Slf4j
public abstract class AbstractParallelRetriever<T> {

    private final Executor executor;

    protected AbstractParallelRetriever(Executor executor) {
        this.executor = executor;
    }

    /**
     * 并行执行检索并只返回合并后的分块列表。
     *
     * @param question 检索问题。
     * @param targets 检索目标列表。
     * @param topK 每个目标的检索条数。
     * @return 所有目标的合并分块结果。
     */
    public final List<RetrievedChunk> executeParallelRetrieval(String question, List<T> targets, int topK) {
        return executeParallelRetrievalWithTargets(question, targets, topK).allChunks();
    }

    /**
     * 并行执行检索，并返回包含目标级映射的完整结果。
     *
     * @param question 检索问题。
     * @param targets 检索目标列表，例如多个 collection。
     * @param topK 每个目标的检索条数。
     * @return 带有合并结果、目标明细与成功失败统计信息的结果对象。
     */
    public final ParallelRetrievalResult<T> executeParallelRetrievalWithTargets(String question, List<T> targets, int topK) {
        record RetrievalFuture<T>(T target, CompletableFuture<List<RetrievedChunk>> future) {
        }

        // 为每个检索目标创建异步任务，由统一线程池并行执行。
        List<RetrievalFuture<T>> futures = targets.stream()
                .map(target -> new RetrievalFuture<>(
                        target,
                        CompletableFuture.supplyAsync(() -> createRetrievalTask(question, target, topK), executor)
                ))
                .toList();

        List<RetrievedChunk> allChunks = new ArrayList<>();
        Map<T, List<RetrievedChunk>> targetChunks = new LinkedHashMap<>();
        int successCount = 0;
        int failureCount = 0;

        for (RetrievalFuture<T> future : futures) {
            try {
                // join 在这里统一回收结果，确保返回前所有检索任务都已完成。
                List<RetrievedChunk> chunks = future.future().join();
                allChunks.addAll(chunks);
                targetChunks.put(future.target(), chunks);
                successCount++;
            } catch (Exception ex) {
                failureCount++;
                // 单个目标失败时不影响整体流程，记录为空列表继续向后执行。
                targetChunks.put(future.target(), List.of());
                log.error("{} failed, target={}", getStatisticsName(), getTargetIdentifier(future.target()), ex);
            }
        }

        log.info("{} finished, targets={}, success={}, failure={}, chunks={}",
                getStatisticsName(), targets.size(), successCount, failureCount, allChunks.size());

        return new ParallelRetrievalResult<>(allChunks, targetChunks, successCount, failureCount);
    }

    /**
     * 创建单个目标的检索任务。
     *
     * @param question 检索问题。
     * @param target 当前目标，例如某个 collection。
     * @param topK 当前目标的检索条数。
     * @return 当前目标命中的检索分块。
     */
    protected abstract List<RetrievedChunk> createRetrievalTask(String question, T target, int topK);

    /**
     * 获取目标日志标识。
     *
     * @param target 当前检索目标。
     * @return 适合打印到日志中的目标标识。
     */
    protected abstract String getTargetIdentifier(T target);

    /**
     * 获取当前检索器的统计名称。
     *
     * @return 统计日志名称。
     */
    protected abstract String getStatisticsName();

    /**
     * 并行检索结果载体。
     *
     * @param allChunks 所有目标命中的合并分块。
     * @param targetChunks 按目标拆分的检索分块映射。
     * @param successCount 成功目标数。
     * @param failureCount 失败目标数。
     * @param <T> 目标类型。
     */
    public record ParallelRetrievalResult<T>(
            List<RetrievedChunk> allChunks,
            Map<T, List<RetrievedChunk>> targetChunks,
            int successCount,
            int failureCount
    ) {
    }
}
