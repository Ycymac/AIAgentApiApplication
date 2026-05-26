package com.ycy.aiapplication.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycy.aiapplication.knowledge.dao.entity.KnowledgeChunkDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface KnowledgeChunkDOMapper extends BaseMapper<KnowledgeChunkDO> {

    Set<String> findVisibleChunkIds(@Param("chunkIds") List<String> chunkIds);
}
