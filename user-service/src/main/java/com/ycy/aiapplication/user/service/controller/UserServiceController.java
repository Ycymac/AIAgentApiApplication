package com.ycy.aiapplication.user.service.controller;

import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import com.ycy.aiapplication.user.service.dto.req.LoginReqDTO;
import com.ycy.aiapplication.user.service.dto.req.LogoutReqDTO;
import com.ycy.aiapplication.user.service.dto.req.SignUpReqDTO;
import com.ycy.aiapplication.user.service.dto.resp.LoginRespDTO;
import com.ycy.aiapplication.user.service.dto.resp.LogoutRespDTO;
import com.ycy.aiapplication.user.service.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/user/service")
@RequiredArgsConstructor
@Tag(name = "用户操作管理")
public class UserServiceController {

    private final UserService userService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginRespDTO> login(@RequestBody LoginReqDTO requestParam) {
        String accountId = requestParam.getAccountId();
        String password = requestParam.getPassword();
        return userService.login(accountId, password);
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    public Result<LogoutRespDTO> logout(@RequestBody LogoutReqDTO requestParam) {
        return userService.logout(requestParam.getAccountId());
    }

    @Operation(summary = "用户注册")
    @PostMapping("/sign/up")
    public Result<Void> signUpNewAccount(@RequestBody SignUpReqDTO requestParam) {
        String accountId = requestParam.getAccountId();
        String password = requestParam.getPassword();
        String nickName = requestParam.getNickName();
        userService.signUpNewAccount(accountId, password, nickName);
        return Results.success();
    }
}
