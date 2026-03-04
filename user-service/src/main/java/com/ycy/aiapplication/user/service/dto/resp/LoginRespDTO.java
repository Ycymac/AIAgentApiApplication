package com.ycy.aiapplication.user.service.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(
            description = "账户id",
            example = "3253984909@qq.com"
    )
    private String accountId;

    /**
     * 昵称
     */
    @Schema(
            description = "账户密码",
            example = "ycy2006721"
    )
    private String nickName;

}
