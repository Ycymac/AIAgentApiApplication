package com.ycy.aiapplication.infrastructure.ai.chat.toolkit;

import com.ycy.aiapplication.infrastructure.ai.config.AIModelProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Chat 模型配置注册表。
 * 负责在配置名、模型名和 provider 之间做轻量映射，不参与执行和降级。
 */
@Component
@RequiredArgsConstructor
public class ChatModelRegistry {

    private final AIModelProperties properties;

    /**
     * 根据配置名或模型名解析最终配置名。
     */
    public String resolveConfigName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = normalize(value);
        for (Map.Entry<String, String> entry : allModels().entrySet()) {
            if (normalize(entry.getKey()).equals(normalized) || normalize(entry.getValue()).equals(normalized)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 解析指定 provider 对应的默认配置名。
     */
    public String resolveDefaultConfigName(String provider) {
        String targetProvider = StringUtils.hasText(provider) ? normalize(provider) : normalize(properties.getChat().getDefaultProvider());
        String selectedConfigName = resolveConfigName(properties.getChat().getDefaultModel());
        if (selectedConfigName != null && Objects.equals(resolveProvider(selectedConfigName), targetProvider)) {
            return selectedConfigName;
        }
        for (Map.Entry<String, String> entry : allModels().entrySet()) {
            String entryProvider = resolveProvider(entry.getKey());
            if (Objects.equals(targetProvider, entryProvider)) {
                return entry.getKey();
            }
        }
        return selectedConfigName;
    }

    /**
     * 通过配置名前缀推导 provider。
     */
    public String resolveProvider(String alias) {
        String normalized = normalize(alias);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("bailian")) {
            return "bailian";
        }
        if (normalized.startsWith("siliconflow")) {
            return "siliconflow";
        }
        return null;
    }

    /**
     * 读取配置名对应的上游模型名。
     */
    public String getModel(String configName) {
        if (!StringUtils.hasText(configName) || properties.getChat().getModels() == null) {
            return null;
        }
        return properties.getChat().getModels().get(configName);
    }

    /**
     * 返回 chat 模型配置。
     */
    public Map<String, String> allModels() {
        return properties.getChat().getModels() == null ? Map.of() : new LinkedHashMap<>(properties.getChat().getModels());
    }

    /**
     * 统一字符串归一化规则。
     */
    public String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }
}
