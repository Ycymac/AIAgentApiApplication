package com.ycy.aiapplication.user.service.common.context;



import java.util.Optional;

/**
 * 用户登录信息存储上下文
 */

public final class UserContext {
    private static final ThreadLocal<UserInfoDTO>USER_THREAD_LOCAL=new ThreadLocal<>();

    public static  void setUser(UserInfoDTO user){USER_THREAD_LOCAL.set(user);}

    public static Long getId(){
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getId).orElse(2028780128997244929L);
    }
    public static String getAccountId(){
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getAccountId).orElse("3253984909@qq.com");
    }

    public static String getNickName(){
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getNickName).orElse("ycy");
    }

    public static void removeUser(){USER_THREAD_LOCAL.remove();}
}
