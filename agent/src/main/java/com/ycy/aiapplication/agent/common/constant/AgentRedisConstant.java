package com.ycy.aiapplication.agent.common.constant;

public class AgentRedisConstant {
    //一个用户对应一个ZSet，用于显示记录\模糊查询
    public static final String INTERVIEW_RECORD_ID_WITH_NAME_CACHE_KEY="ai-application:agent:record_id_ZSet:accountId:%s";

}
