package com.ycy.aiapplication.user.service.dto.req;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录表
 * 我们这里不涉及第三方的验证码登录，直接使用用户名+密码
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginReqDTO{
    @Schema(
            description = "账户id，登录使用",
            example = "3253989909@qq.com",
            required = true

    )
    /**
     * 账户id
     */
    private String accountId;

    @Schema(
            description = "账户密码，仅限登录使用",
            example = "12345678",
            required = true

    )
    /**
     * 账户密码
     */
    private String password;

}
