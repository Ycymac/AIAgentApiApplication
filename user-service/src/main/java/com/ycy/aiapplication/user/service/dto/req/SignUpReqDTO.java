package com.ycy.aiapplication.user.service.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 注册表
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SignUpReqDTO {
    @Schema(
            description = "账户id",
            example = "3253984909@qq.com"
    )
    private String accountId;
    @Schema(
            description = "账户密码",
            example = "ycy2006721"
    )
    private String password;
    @Schema(
            description = "账户昵称",
            example = "ycy"
    )
    private String nickName;

}
