package com.ycy.aiapplication.infrastructure.ai.model;

import com.ycy.aiapplication.framework.exception.RemoteException;
import com.ycy.aiapplication.infrastructure.ai.enums.ModelCapability;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * 模型调度执行器
 * <p>
 * 负责：
 * 1.故障转移
 * 2.健康检查：调用前检查模型健康状态，跳过不健康模型
 * 3.统一模型执行逻辑
 * 4.成功/失败状态记录：自动更新当前熔断器状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelRoutingExecutor {

    private final ModelHealthStore healthStore;

    /**
     * 带有降级策略的执行方法
     * @param capability 模型能力枚举，代表当前模型模型
     * @param targets 所有可用的模型列表
     * @param clientResolver 函数式接口，通过不同的包装好的模型找到对应的客户端（Client）实现
     * @param caller 自定义函数式接口，定义客户端执行具体逻辑
     * @param <C> Client 执行客户端
     * @param <T> 返回值结果
     */
    public <C, T> T executeWithFallback(
            ModelCapability capability,
            List<ModelTarget> targets,
            Function<ModelTarget, C> clientResolver,
            ModelCaller<C, T> caller) {
        String label = capability.getDisplayName();
        if (targets == null || targets.isEmpty()) {
            throw new RemoteException(label + " 模型不可用");
        }

        Throwable last = null;
        for (ModelTarget target : targets) {
            //执行target查找client方法
            C client = clientResolver.apply(target);
            if (client == null || !healthStore.allowCall(target.id())) {
                continue;
            }
            try {
                T result = caller.call(client, target);
                healthStore.markSuccess(target.id());
                return result;
            } catch (Exception ex) {
                last = ex;
                healthStore.markFailure(target.id());
                log.warn("{} 模型执行失败,降级执行. 模型提供商={},模型={}", label, target.provider(), target.model(), ex);
            }
        }

        throw new RemoteException(
                "所有" + label + "模型执行失败: " + (last == null ? "unknown" : last.getMessage()),
                last,
                com.ycy.aiapplication.framework.errorcode.BaseErrorCode.REMOTE_ERROR
        );
    }
}
