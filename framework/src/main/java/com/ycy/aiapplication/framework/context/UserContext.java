package com.ycy.aiapplication.framework.context;



import lombok.Setter;

import java.util.Optional;

/**
 * 用户登录信息存储上下文
 */

public final class UserContext {
    private static final ThreadLocal<UserInfoDTO>USER_THREAD_LOCAL=new ThreadLocal<>();
    @Setter
    private static volatile UserNickNameResolver userNickNameResolver;

    public static  void setUser(UserInfoDTO user){USER_THREAD_LOCAL.set(user);}

    public static Long getId(){
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getId).orElse(null);
    }
    public static String getAccountId(){
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getAccountId).orElse(null);
    }

    public static String getNickName(){
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        if (userInfoDTO == null || userNickNameResolver == null) {
            return null;
        }
        return userNickNameResolver.getNickName(userInfoDTO.getAccountId());
    }

    public static String getJti() {
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getJti).orElse(null);
    }

    public static Long getLoginExpireTime() {
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getLoginExpireTime).orElse(null);
    }

    public static boolean isManager(){
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        if(userInfoDTO==null)return false;
        return userInfoDTO.getPermission()==0;
    }

    public static void removeUser(){USER_THREAD_LOCAL.remove();}
}
