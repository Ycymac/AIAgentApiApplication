package com.ycy.aiapplication.user.service.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录返回结果，不应当包含密码
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginRespDTO {
    /**
     * 账户id
     */
    private String accountId;
    /**
     * 昵称
     */
    private String nickName;

}
