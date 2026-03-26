package com.ycy.aiapplication.user.service.service;


import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.user.service.dto.resp.LogoutRespDTO;
import com.ycy.aiapplication.user.service.dto.resp.LoginRespDTO;

public interface UserService {

    /**
     * 验证账户和密码
     * @param accountId 账户id
     * @param password 账户密码
     * @return 返回对应的账户信息用于登录界面等
     */
   Result<LoginRespDTO> login(String accountId, String password);

    /**
     * 用户登出
     * @param accountId 账户id
     * @return 登出结果
     */
   Result<LogoutRespDTO> logout(String accountId);

    /**
     * 创建新账户
     * @param accountId 账户id
     * @param password 密码
     * @param nickName 昵称
     * 这里无需返回值，一旦无法创建直接报错
     */
   void signUpNewAccount(String accountId,String password,String nickName);

}
