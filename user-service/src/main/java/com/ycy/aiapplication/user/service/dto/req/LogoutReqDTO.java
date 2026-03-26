package com.ycy.aiapplication.user.service.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登出请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LogoutReqDTO {

    @Schema(
            description = "账户id，登出使用",
            example = "3253989909@qq.com",
            required = true

    )
    private String accountId;
}
