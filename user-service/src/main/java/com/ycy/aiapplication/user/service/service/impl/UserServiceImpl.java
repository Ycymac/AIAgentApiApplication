package com.ycy.aiapplication.user.service.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycy.aiapplication.framework.errorcode.BaseErrorCode;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import com.ycy.aiapplication.user.service.common.constant.UserServiceRedisConstant;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.user.service.dao.entity.UserAccountDO;
import com.ycy.aiapplication.user.service.dao.mapper.UserAccountDOMapper;
import com.ycy.aiapplication.user.service.dto.resp.LoginRespDTO;
import com.ycy.aiapplication.user.service.dto.resp.LogoutRespDTO;
import com.ycy.aiapplication.user.service.service.UserService;
import com.ycy.aiapplication.user.service.toolkit.JWTUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private  final UserAccountDOMapper userAccountDOMapper;
    private final JWTUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result<LoginRespDTO> login(String accountId, String password) {

        LambdaQueryWrapper<UserAccountDO> queryWrapper = new LambdaQueryWrapper<UserAccountDO>()
                .eq(UserAccountDO::getAccountId, accountId);

        UserAccountDO userAccountDO = userAccountDOMapper.selectOne(queryWrapper);
        if(ObjectUtil.isNull(userAccountDO)||!userAccountDO.getPassword().equals(password)){
            log.error("账户密码校验异常，登录账户：{},查询到的账户：{}",accountId, Optional.ofNullable(userAccountDO).map(UserAccountDO::getAccountId).orElse("未查询到对应账户"));
            throw new ClientException(BaseErrorCode.PASSWORD_VERIFY_ERROR);
        }
        log.info("校验成功，生成jwt令牌");
        String token = jwtUtil.generateToken(accountId);
        LoginRespDTO loginRespDTO = LoginRespDTO.builder()
                .accountId(accountId)
                .nickName(userAccountDO.getNickName())
                .build();
        return Results.successWithToken(loginRespDTO,token);

    }

    @Override
    public Result<LogoutRespDTO> logout(String accountId) {
        String currentAccountId = UserContext.getAccountId();
        String currentNickName = UserContext.getNickName();
        if(StrUtil.isEmpty(currentAccountId)){
            throw new ClientException("当前用户未登录或登录已失效，请检查！");
        }
        if(StrUtil.isNotEmpty(accountId) && !currentAccountId.equals(accountId)){
            log.error("登出账户与当前登录账户不匹配，requestAccountId:{}, currentAccountId:{}", accountId, currentAccountId);
            throw new ClientException("登出用户信息异常，请检查！");
        }
        UserContext.removeUser();
        log.info("用户登出成功，accountId:{}", currentAccountId);
        return Results.success(LogoutRespDTO.builder()
                .accountId(currentAccountId)
                .nickName(currentNickName)
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signUpNewAccount(String accountId, String password, String nickName) {
        if(StrUtil.isEmpty(accountId)||StrUtil.isEmpty(password)||StrUtil.isEmpty(nickName)){
            log.error("用户存在信息为空，accountId:{}is empty,password{}is empty,nickName{}is empty",StrUtil.isEmpty(accountId),StrUtil.isEmpty(password),StrUtil.isEmpty(nickName));
            throw new ClientException("用户注册信息不完整，请检查！");
        }
        String cacheKey = String.format(UserServiceRedisConstant.USER_ACCOUNT_CACHE_KEY, accountId);
        Boolean exists = stringRedisTemplate.hasKey(cacheKey);
        if (Boolean.TRUE.equals(exists)) {
            log.error("用户使用的id已经存在！账户id：{}",accountId);
            throw new ClientException("注册用户失败！当前用户id已经存在！");
        }

        stringRedisTemplate.opsForValue().set(cacheKey,nickName);

        UserAccountDO newUserAccountDO = UserAccountDO.builder()
                .accountId(accountId)
                .password(password)
                .nickName(nickName)
                .deleted(false)
                .build();

        userAccountDOMapper.insert(newUserAccountDO);
        log.info("新用户注册成功，已经缓存到redis当中，并存储到mysql数据库当中");
    }
}
