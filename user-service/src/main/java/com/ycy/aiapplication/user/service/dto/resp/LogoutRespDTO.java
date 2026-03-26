package com.ycy.aiapplication.user.service.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登出返回结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LogoutRespDTO {

    @Schema(
            description = "账户id",
            example = "3253984909@qq.com"
    )
    private String accountId;

    @Schema(
            description = "账户昵称",
            example = "ycy"
    )
    private String nickName;
}
