package com.ycy.aiapplication.user.service.dto.req;

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

    private String accountId;

    private String password;

    private String nickName;

}
