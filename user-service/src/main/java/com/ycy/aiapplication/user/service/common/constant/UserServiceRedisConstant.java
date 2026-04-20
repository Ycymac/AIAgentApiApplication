package com.ycy.aiapplication.user.service.common.constant;

public class UserServiceRedisConstant {
    public static final String USER_ACCOUNT_CACHE_KEY="ai-application_user-service:user_account:%s";
    public static final String INTERVIEWEE_FORM_NAME_CACHE_KEY="ai-application_user-service:interviewee_form_name_zset:userId:%s";
    public static final String LOGIN_CACHE_KEY = "javis-login%s";
    public static final String USER_NICK_NAME_CACHE_KEY = "javis-nickName%s";
    public static final String LOGIN_CACHE_VALUE_SEPARATOR = "|";
    public static final long LOGIN_CACHE_TTL_HOURS = 10L;
}
