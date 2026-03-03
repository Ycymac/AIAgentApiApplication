package com.ycy.aiapplication.user.service.controller;

import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import com.ycy.aiapplication.user.service.dto.req.LoginReqDTO;
import com.ycy.aiapplication.user.service.dto.req.SignUpReqDTO;
import com.ycy.aiapplication.user.service.dto.resp.LoginRespDTO;
import com.ycy.aiapplication.user.service.service.UserLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/user/service")
@RequiredArgsConstructor
public class UserServiceController {

    private final UserLoginService userLoginService;


    @PostMapping("/login")
    public Result<LoginRespDTO> login(@RequestBody LoginReqDTO requestParam){
        String accountId = requestParam.getAccountId();
        String password = requestParam.getPassword();
        return userLoginService.login(accountId,password);
    }

    @PostMapping("/sign/up")
    public Result<Void> signUpNewAccount(@RequestBody SignUpReqDTO requestParam){
        String accountId =requestParam.getAccountId();
        String password = requestParam.getPassword();
        String nickName= requestParam.getNickName();
        userLoginService.signUpNewAccount(accountId,password,nickName);
        return Results.success();
    }
}
