package com.ycy.aiapplication.rag.core.intent.management;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycy.aiapplication.rag.core.intent.common.IntentNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 知识库意图节点缓存管理器。
 * 作用：
 * 1. 仅缓存第二层识别所需的知识库节点。
 * 2. 为二层分类器提供快速加载节点的能力，减少频繁访问数据库。
 * 3. 在节点同步或变更后负责清理和回填缓存。
 *<p>
 * 调用顺序：
 * 1. 二层分类器先调用 {@link #getKnowledgeNodesFromCache()}。
 * 2. 若缓存未命中，则由管理服务回源数据库，再调用 {@link #saveKnowledgeNodesToCache(List)} 回填。
 * 3. 当节点有变更时，由管理服务调用 {@link #clearKnowledgeNodesCache()} 主动失效旧缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentTreeCacheManager {

    private static final String KB_INTENT_CACHE_KEY = "ai_application:rag:intent:kb_nodes";
    private static final long CACHE_EXPIRE_DAYS = 7L;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 从 Redis 中读取知识库意图节点缓存。
     *
     * @return 节点列表；缓存不存在或读取失败时返回 null
     */
    public List<IntentNode> getKnowledgeNodesFromCache() {
        try {
            String cacheJson = stringRedisTemplate.opsForValue().get(KB_INTENT_CACHE_KEY);
            if (cacheJson == null) {
                return null;
            }
            return objectMapper.readValue(cacheJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.warn("读取知识库意图节点缓存失败", ex);
            return null;
        }
    }

    /**
     * 写入知识库意图节点缓存。
     *
     * @param nodes 当前可用的知识库意图节点列表
     */
    public void saveKnowledgeNodesToCache(List<IntentNode> nodes) {
        try {
            stringRedisTemplate.opsForValue().set(
                    KB_INTENT_CACHE_KEY,
                    // 统一序列化为 JSON，方便跨服务和后续结构演进。
                    objectMapper.writeValueAsString(nodes),
                    CACHE_EXPIRE_DAYS,
                    TimeUnit.DAYS
            );
        } catch (Exception ex) {
            log.warn("保存知识库意图节点缓存失败", ex);
        }
    }

    /**
     * 主动清理知识库意图节点缓存。
     */
    public void clearKnowledgeNodesCache() {
        try {
            stringRedisTemplate.delete(KB_INTENT_CACHE_KEY);
        } catch (Exception ex) {
            log.warn("清理知识库意图节点缓存失败", ex);
        }
    }
}
