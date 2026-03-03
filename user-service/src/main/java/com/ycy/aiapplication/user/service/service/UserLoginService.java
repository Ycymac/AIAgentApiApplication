package com.ycy.aiapplication.user.service.service;


import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.user.service.dto.resp.LoginRespDTO;

public interface UserLoginService {

    /**
     * 验证账户和密码
     * @param accountId 账户id
     * @param password 账户密码
     * @return 返回对应的账户信息用于登录界面等
     */
   Result<LoginRespDTO> login(String accountId, String password);


}
