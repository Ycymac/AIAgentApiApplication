package com.ycy.aiapplication.infrastructure.ai.chat.toolkit;

import cn.hutool.core.collection.CollUtil;
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
import java.util.Set;

/**
 * Chat 候选模型选择器。
 * <p>
 * 候选链固定为：用户指定模型或默认模型 -> 系统隐藏兜底模型列表。
 */
@Component
@RequiredArgsConstructor
public class ChatModelSelector {

    private final AIModelProperties properties;
    private final ChatModelRegistry modelRegistry;
    private final ModelHealthStore healthStore;

    public List<ModelTarget> select(ChatRequest request) {
        String primaryConfigName = resolvePrimaryConfigName(request);
        List<ModelTarget> targets = new ArrayList<>();
        Set<String> targetKeys = new LinkedHashSet<>();
        addConfiguredTarget(targets, targetKeys, primaryConfigName);
        addBackupTargets(targets, targetKeys);
        return targets.stream().filter(target -> !healthStore.isOpen(target.id())).toList();
    }

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

    private void addConfiguredTarget(List<ModelTarget> targets, Set<String> targetKeys, String configName) {
        addTarget(
                targets,
                targetKeys,
                configName,
                modelRegistry.resolveProvider(configName),
                modelRegistry.getModel(configName)
        );
    }

    private void addBackupTargets(List<ModelTarget> targets, Set<String> targetKeys) {
        List<AIModelProperties.Chat.BackupModel> backupModels = properties.getChat().getBackupModels();
        if (CollUtil.isEmpty(backupModels)) {
            return;
        }
        for (AIModelProperties.Chat.BackupModel backupModel : backupModels) {
            if (backupModel == null) {
                continue;
            }
            String provider = modelRegistry.normalize(backupModel.getProvider());
            String model = backupModel.getModel();
            String id = StringUtils.hasText(backupModel.getId())
                    ? backupModel.getId()
                    : provider + "_backup";
            addTarget(targets, targetKeys, id, provider, model);
        }
    }

    private void addTarget(List<ModelTarget> targets, Set<String> targetKeys, String id, String provider, String model) {
        if (!StringUtils.hasText(id) || !StringUtils.hasText(provider) || !StringUtils.hasText(model)) {
            return;
        }
        String targetKey = modelRegistry.normalize(provider) + ":" + modelRegistry.normalize(model);
        if (targetKeys.add(targetKey)) {
            targets.add(new ModelTarget(id, provider, model));
        }
    }
}
