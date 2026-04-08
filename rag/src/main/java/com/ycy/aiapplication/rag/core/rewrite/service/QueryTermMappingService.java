package com.ycy.aiapplication.rag.core.rewrite.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycy.aiapplication.rag.core.rewrite.common.QueryTermMappingUtil;
import com.ycy.aiapplication.rag.dao.entity.QueryTermMappingDO;
import com.ycy.aiapplication.rag.dao.mapper.QueryTermMappingMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 术语替换服务类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryTermMappingService {
    private final QueryTermMappingMapper mappingMapper;
    //按照优先级、长度缓存好的匹配规则
    private volatile List<QueryTermMappingDO> cachedMappings = List.of();

    @PostConstruct
    public void loadMappings() {
        List<QueryTermMappingDO> dbList = mappingMapper.selectList(
                Wrappers.lambdaQuery(QueryTermMappingDO.class)
                        .eq(QueryTermMappingDO::getEnabled, 1)
        );
        //按照优先级搞、sourceTerm更长的排在前
        dbList.sort(
                //第一个参数：对象当中提取的比较字段；  第二个参数：定义如何比较——这里的是两者为Integer类型，执行比较的同时，值为null的排在后面
                Comparator.comparing(QueryTermMappingDO::getPriority, Comparator.nullsLast(Integer::compareTo))
                        .reversed()
                        .thenComparing(m -> m.getSourceTerm() == null ? 0 : m.getSourceTerm().length(), Comparator.reverseOrder())
        );
        cachedMappings = dbList;
        log.info("查询归一化映射规则加载完成, 共加载 {} 条规则", cachedMappings.size());
    }

    /**
     * 对用户问题做术语归一化
     */
    public String normalize(String text) {
        if (text == null || text.isEmpty() || cachedMappings.isEmpty()) {
            return text;
        }
        String result = text;
        //按照术语排序规则进行替换
        for (QueryTermMappingDO mapping : cachedMappings) {
            //跳过禁止匹配的项
            if (mapping.getEnabled() == null || mapping.getEnabled() == 0) {
                continue;
            }
            if (mapping.getMatchType() != null && mapping.getMatchType() != 1) {
                // 这里只示例 match_type = 1 的简单子串匹配，其他类型可以自己扩展
                continue;
            }
            String source = mapping.getSourceTerm();
            String target = mapping.getTargetTerm();
            if (source == null || source.isEmpty() || target == null || target.isEmpty()) {
                continue;
            }
            result = QueryTermMappingUtil.applyMapping(result, source, target);
        }

        if (!Objects.equals(text, result)) {
            log.info("查询归一化：original='{}', normalized='{}'", text, result);
        }
        return result;
    }
}
