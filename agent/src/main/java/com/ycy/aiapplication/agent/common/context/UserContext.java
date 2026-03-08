package com.ycy.aiapplication.agent.common.context;

import java.util.Optional;

/**
 * 用户登录信息存储上下文
 */

public final class UserContext {
    private static final ThreadLocal<UserInfoDTO>USER_THREAD_LOCAL=new ThreadLocal<>();

    public static  void setUser(UserInfoDTO user){USER_THREAD_LOCAL.set(user);}

    public static Long getId(){
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        //这里测试使用
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getId).orElse(2028780128997244929L);
       /* //测试用，使用固定替代
        return 2028780128997244929L;*/
    }

    public static String getAccountId(){
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getAccountId).orElse(null);
    }

    public static String getNickName(){
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getNickName).orElse(null);
    }

    public static void removeUser(){USER_THREAD_LOCAL.remove();}
}