package com.ycy.aiapplication.rag.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycy.aiapplication.rag.control.vo.ConversationMessageVO;
import com.ycy.aiapplication.rag.dao.entity.ConversationDO;
import com.ycy.aiapplication.rag.dao.entity.ConversationMessageDO;
import com.ycy.aiapplication.rag.dao.mapper.ConversationMapper;
import com.ycy.aiapplication.rag.dao.mapper.ConversationMessageMapper;
import com.ycy.aiapplication.rag.enums.ConversationMessageOrder;
import com.ycy.aiapplication.rag.service.ConversationMessageService;
import com.ycy.aiapplication.rag.service.bo.ConversationMessageBO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationMessageServiceImpl implements ConversationMessageService {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper conversationMessageMapper;

    @Override
    public String addMessage(ConversationMessageBO conversationMessage) {
        ConversationMessageDO messageDO = BeanUtil.toBean(conversationMessage, ConversationMessageDO.class);
        conversationMessageMapper.insert(messageDO);
        return messageDO.getId();
    }

    @Override
    public List<ConversationMessageVO> listMessages(String conversationId, String userId, Integer limit, ConversationMessageOrder order) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            return List.of();
        }

        ConversationDO conversation = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, conversationId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );
        if (conversation == null) {
            return List.of();
        }

        boolean asc = order == null || order == ConversationMessageOrder.ASC;
        //截取最新（DESC）/最旧（ASC）
        List<ConversationMessageDO> records = conversationMessageMapper.selectList(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId, conversationId)
                        .eq(ConversationMessageDO::getDeleted, 0)
                        //按照时间正序获取limit条的，拿到的就是从“旧”的到“新”的——也就是最老的limit条消息
                        //按照时间倒序获取limit条的，拿到的是按照时间倒序的（从“新”到“旧”的）——最新的limit条消息
                        .orderBy(true, asc, ConversationMessageDO::getCreateTime)
                        .last(limit != null, "limit " + limit)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        if (!asc) {
            //按照时间倒序获取的顺序也是从新到旧，我们需要按照时间正序排序
            Collections.reverse(records);
        }
        //删除原来用户反馈逻辑，个人认为不太需要这个逻辑，用户对问题有问题一般会直接在下一个对话指正（类似真实聊天）
        List<ConversationMessageVO> result = new ArrayList<>();
        for (ConversationMessageDO record : records) {
            ConversationMessageVO vo = ConversationMessageVO.builder()
                    .id(String.valueOf(record.getId()))
                    .conversationId(record.getConversationId())
                    .role(record.getRole())
                    .content(record.getContent())
                    .createTime(record.getCreateTime())
                    .build();
            result.add(vo);
        }

        return result;
    }
}
