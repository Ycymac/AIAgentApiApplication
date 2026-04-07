package com.ycy.aiapplication.infrastructure.ai.chat.toolkit;

import com.ycy.aiapplication.framework.convention.ChatRequest;
import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import com.ycy.aiapplication.infrastructure.ai.model.ModelHealthStore;
import com.ycy.aiapplication.infrastructure.ai.model.ModelTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Chat 候选模型选择器。
 * 仅负责从配置中构造主模型和平台内兜底模型链，不改变后续路由执行方式。
 */
@Component
@RequiredArgsConstructor
public class ChatModelSelector {

    private final AIModelProperties properties;
    private final ChatModelRegistry modelRegistry;
    private final ModelHealthStore healthStore;

    /**
     * 为当前请求生成候选模型列表。
     */
    public List<ModelTarget> select(ChatRequest request) {
        String primaryConfigName = resolvePrimaryConfigName(request);
        List<ModelTarget> targets = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        addConfiguredTarget(targets, ids, primaryConfigName);
        addFallbackTarget(targets, ids, primaryConfigName);
        return targets.stream().filter(target -> !healthStore.isOpen(target.id())).toList();
    }

    /**
     * 解析请求优先命中的模型配置名。
     */
    private String resolvePrimaryConfigName(ChatRequest request) {
        String requestedModel = modelRegistry.resolveConfigName(request.getModelId());
        String requestedProvider = modelRegistry.normalize(request.getProvider());
        if (requestedModel != null) {
            return requestedModel;
        }
        if (StringUtils.hasText(requestedProvider)) {
            String providerDefault = modelRegistry.resolveDefaultConfigName(requestedProvider);
            if (providerDefault != null) {
                return providerDefault;
            }
        }
        String defaultModel = modelRegistry.resolveDefaultConfigName(properties.getChat().getDefaultProvider());
        if (defaultModel != null) {
            return defaultModel;
        }
        throw new IllegalStateException("No chat model configured");
    }

    /**
     * 根据主模型所属平台决定是否追加平台内兜底模型。
     */
    private void addFallbackTarget(List<ModelTarget> targets, Set<String> ids, String primaryConfigName) {
        String provider = modelRegistry.resolveProvider(primaryConfigName);
        String primaryModel = modelRegistry.getModel(primaryConfigName);
        String fallbackModel;
        String fallbackConfigName;
        if (Objects.equals("bailian", provider)) {
            if (!Boolean.TRUE.equals(properties.getChat().getBailianFallbackEnabled())) {
                return;
            }
            fallbackModel = properties.getChat().getBailianBackUpModel();
            fallbackConfigName = "bailian_backup";
        } else if (Objects.equals("siliconflow", provider)) {
            if (!Boolean.TRUE.equals(properties.getChat().getSiliconFlowFallbackEnabled())) {
                return;
            }
            fallbackModel = properties.getChat().getSiliconFlowBackUpModel();
            fallbackConfigName = "siliconflow_backup";
        } else {
            return;
        }
        if (!StringUtils.hasText(fallbackModel) || fallbackModel.equals(primaryModel)) {
            return;
        }
        addTarget(targets, ids, fallbackConfigName, provider, fallbackModel);
    }

    /**
     * 把已有配置模型加入候选链。
     */
    private void addConfiguredTarget(List<ModelTarget> targets, Set<String> ids, String configName) {
        addTarget(targets, ids, configName, modelRegistry.resolveProvider(configName), modelRegistry.getModel(configName));
    }

    /**
     * 向候选链中追加一个目标，并使用配置名去重。
     */
    private void addTarget(List<ModelTarget> targets, Set<String> ids, String configName, String provider, String model) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(model)) {
            return;
        }
        if (ids.add(configName)) {
            // 直接使用配置名作为稳定标识，便于日志、熔断状态和配置项一一对应。
            targets.add(new ModelTarget(configName, provider, model));
        }
    }
}
