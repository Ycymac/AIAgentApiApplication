package com.ycy.aiapplication.user.service.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycy.aiapplication.framework.errorcode.BaseErrorCode;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import com.ycy.aiapplication.user.service.dao.entity.UserAccountDO;
import com.ycy.aiapplication.user.service.dao.mapper.UserAccountDOMapper;
import com.ycy.aiapplication.user.service.dto.resp.LoginRespDTO;
import com.ycy.aiapplication.user.service.service.UserLoginService;
import com.ycy.aiapplication.user.service.toolkit.JWTUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoginServiceImpl implements UserLoginService {

    private  final UserAccountDOMapper userAccountDOMapper;

    private final JWTUtil jwtUtil;

    /**
     * 登录校验
     * @param accountId 账户id
     * @param password 账户密码
     * @return 登录信息+token
     */
    @Override
    public Result<LoginRespDTO> login(String accountId, String password) {

        LambdaQueryWrapper<UserAccountDO> queryWrapper = new LambdaQueryWrapper<UserAccountDO>()
                .eq(UserAccountDO::getAccountId, accountId)
                .eq(UserAccountDO::getPassword, password);

        UserAccountDO userAccountDO = userAccountDOMapper.selectOne(queryWrapper);
        //当前未进行密码加密，直接进行明文比较
        if(ObjectUtil.isNull(userAccountDO)||!userAccountDO.getPassword().equals(password)){
            log.error("账户密码校验异常，登录账户：{},查询到的账户：{}",accountId, Optional.ofNullable(userAccountDO.getAccountId()).orElse("未查询到对应账户"));
            throw new ClientException(BaseErrorCode.PASSWORD_VERIFY_ERROR);
        }
        //验证成功，生成token
        String token = jwtUtil.generateToken(accountId);
        LoginRespDTO loginRespDTO = LoginRespDTO.builder()
                .accountId(accountId)
                .nickName(userAccountDO.getNickName())
                .build();
        return Results.successWithToken(loginRespDTO,token);

    }
}
