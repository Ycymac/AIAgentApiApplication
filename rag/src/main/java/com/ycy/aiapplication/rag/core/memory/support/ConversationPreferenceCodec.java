package com.ycy.aiapplication.rag.core.memory.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.ycy.aiapplication.rag.core.memory.common.PreferenceItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 会话偏好的 JSON 编解码、规范化与并发合并工具。
 */
public final class ConversationPreferenceCodec {

    /**
     * 单个会话最多保留的偏好数量，防止偏好字段无限增长。
     */
    private static final int MAX_ITEMS = 100;

    /**
     * 单条偏好的最大字符数。
     */
    private static final int MAX_ITEM_CHARS = 200;

    private ConversationPreferenceCodec() {
    }

    /**
     * 将数据库 JSON 解析为规范化偏好列表。
     *
     * @param json 数据库存储的偏好 JSON
     * @return 去除非法项后的不可变列表
     */
    public static List<PreferenceItem> parse(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        return normalize(JSON.parseArray(json, PreferenceItem.class));
    }

    /**
     * 将偏好列表规范化后序列化为 JSON。
     *
     * @param items 待保存的偏好
     * @return 可直接写入数据库的 JSON
     */
    public static String toJson(List<PreferenceItem> items) {
        return JSON.toJSONString(normalize(items));
    }

    /**
     * 将本轮新提取的偏好追加到当前快照。
     *
     * @param current         当前偏好快照
     * @param sourceMessageId 产生这些偏好的用户消息 ID
     * @param contents        本轮提取的偏好文本
     * @return 按内容去重并限制数量后的新快照
     */
    public static List<PreferenceItem> append(List<PreferenceItem> current,
                                              String sourceMessageId,
                                              List<String> contents) {
        List<PreferenceItem> result = new ArrayList<>(normalize(current));
        Set<String> knownContents = new LinkedHashSet<>();
        result.forEach(item -> knownContents.add(item.getContent()));

        // 同一条用户消息可能提取多项偏好，使用消息 ID 和序号生成稳定标识。
        int sequence = 1;
        for (String content : contents == null ? List.<String>of() : contents) {
            String normalizedContent = normalizeContent(content);
            if (StrUtil.isBlank(normalizedContent) || !knownContents.add(normalizedContent)) {
                sequence++;
                continue;
            }
            result.add(new PreferenceItem(
                    sourceMessageId + "-" + sequence,
                    sourceMessageId,
                    normalizedContent
            ));
            sequence++;
            if (result.size() >= MAX_ITEMS) {
                break;
            }
        }
        return List.copyOf(result);
    }

    /**
     * 在摘要压缩写回前，合并快照之后并发追加的新偏好。
     * <p>
     * 压缩结果负责整理旧快照，latest 中相对 snapshot 新增的偏好必须保留，
     * 防止异步压缩覆盖正在写入的用户偏好。
     *
     * @param normalizedCandidate 压缩模型整理后的候选偏好
     * @param snapshot            压缩开始时读取的偏好快照
     * @param latest              写回前再次读取的最新偏好
     * @return 同时包含压缩结果和并发新增项的规范化列表
     */
    public static List<PreferenceItem> mergeChangesAfterSnapshot(List<PreferenceItem> normalizedCandidate,
                                                                 List<PreferenceItem> snapshot,
                                                                 List<PreferenceItem> latest) {
        List<PreferenceItem> result = new ArrayList<>(normalize(normalizedCandidate));
        Set<String> snapshotIds = new HashSet<>();
        normalize(snapshot).forEach(item -> snapshotIds.add(item.getId()));
        Set<String> resultIds = new HashSet<>();
        result.forEach(item -> resultIds.add(item.getId()));

        for (PreferenceItem item : normalize(latest)) {
            if (!snapshotIds.contains(item.getId()) && resultIds.add(item.getId())) {
                result.add(item);
            }
        }
        return normalize(result);
    }

    /**
     * 校验、裁剪并按 ID 去重偏好列表。
     *
     * @param items 原始偏好列表
     * @return 满足数量和长度限制的不可变列表
     */
    public static List<PreferenceItem> normalize(List<PreferenceItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<PreferenceItem> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (PreferenceItem item : items) {
            if (item == null || StrUtil.isBlank(item.getId()) || StrUtil.isBlank(item.getContent())) {
                continue;
            }
            String content = normalizeContent(item.getContent());
            if (StrUtil.isBlank(content) || !ids.add(item.getId().trim())) {
                continue;
            }
            result.add(new PreferenceItem(
                    item.getId().trim(),
                    StrUtil.blankToDefault(item.getSourceMessageId(), "").trim(),
                    content
            ));
            if (result.size() >= MAX_ITEMS) {
                break;
            }
        }
        return List.copyOf(result);
    }

    /**
     * 清理单条偏好内容并执行长度上限。
     *
     * @param content 原始偏好文本
     * @return 规范化后的文本
     */
    private static String normalizeContent(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        String trimmed = content.trim();
        return trimmed.length() <= MAX_ITEM_CHARS ? trimmed : trimmed.substring(0, MAX_ITEM_CHARS);
    }
}
