package com.ycy.aiapplication.user.service.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.errorcode.BaseErrorCode;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import com.ycy.aiapplication.user.service.common.constant.UserServiceRedisConstant;
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
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserAccountDOMapper userAccountDOMapper;
    private final JWTUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 用户登录方法实现类
     * @param accountId 账户id
     * @param password 账户密码
     */
    @Override
    public Result<LoginRespDTO> login(String accountId, String password) {
        //查验账户是否存在并进行密码校验
        LambdaQueryWrapper<UserAccountDO> queryWrapper = new LambdaQueryWrapper<UserAccountDO>()
                .eq(UserAccountDO::getAccountId, accountId);
        UserAccountDO userAccountDO = userAccountDOMapper.selectOne(queryWrapper);
        if (ObjectUtil.isNull(userAccountDO) || !userAccountDO.getPassword().equals(password)) {
            log.error("Login password verify failed, accountId={}, dbAccount={}",
                    accountId,
                    Optional.ofNullable(userAccountDO).map(UserAccountDO::getAccountId).orElse("NOT_FOUND"));
            throw new ClientException(BaseErrorCode.PASSWORD_VERIFY_ERROR);
        }
        //生成jti
        String jti = IdUtil.getSnowflakeNextIdStr();
        //使用jti+用户主键+用户账户id生成jwt
        String token = jwtUtil.generateToken(userAccountDO.getId(), accountId, jti);
        cacheLoginInfo(userAccountDO, jti);
        cacheNickName(accountId, userAccountDO.getNickName());

        LoginRespDTO loginRespDTO = LoginRespDTO.builder()
                .accountId(accountId)
                .nickName(userAccountDO.getNickName())
                .permission(userAccountDO.getPermission())
                .build();
        return Results.successWithToken(loginRespDTO, token);
    }

    @Override
    public Result<LogoutRespDTO> logout(String accountId) {
        String currentAccountId = UserContext.getAccountId();
        String currentNickName = UserContext.getNickName();
        String currentJti = UserContext.getJti();
        if (StrUtil.isBlank(currentAccountId)) {
            throw new ClientException("Current user is not logged in or token is invalid");
        }
        if (StrUtil.isNotBlank(accountId) && !StrUtil.equals(currentAccountId, accountId)) {
            log.error("Logout account mismatch, requestAccountId={}, currentAccountId={}", accountId, currentAccountId);
            throw new ClientException("Logout account mismatch");
        }

        if (StrUtil.isNotBlank(currentJti)) {
            stringRedisTemplate.delete(String.format(UserServiceRedisConstant.LOGIN_CACHE_KEY, currentJti));
        }
        UserContext.removeUser();
        log.info("Logout success, accountId={}", currentAccountId);
        return Results.success(LogoutRespDTO.builder()
                .accountId(currentAccountId)
                .nickName(currentNickName)
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signUpNewAccount(String accountId, String password, String nickName) {
        if (StrUtil.isBlank(accountId) || StrUtil.isBlank(password) || StrUtil.isBlank(nickName)) {
            log.error("Sign up request contains blank field, accountIdBlank={}, passwordBlank={}, nickNameBlank={}",
                    StrUtil.isBlank(accountId), StrUtil.isBlank(password), StrUtil.isBlank(nickName));
            throw new ClientException("Sign up request is incomplete");
        }
        String cacheKey = String.format(UserServiceRedisConstant.USER_ACCOUNT_CACHE_KEY, accountId);
        Boolean exists = stringRedisTemplate.hasKey(cacheKey);
        if (Boolean.TRUE.equals(exists)) {
            log.error("Sign up account already exists in cache, accountId={}", accountId);
            throw new ClientException("Account already exists");
        }

        stringRedisTemplate.opsForValue().set(cacheKey, nickName);
        UserAccountDO newUserAccountDO = UserAccountDO.builder()
                .accountId(accountId)
                .password(password)
                .nickName(nickName)
                .deleted(false)
                .build();
        userAccountDOMapper.insert(newUserAccountDO);
        log.info("Sign up success, accountId={}", accountId);
    }

    private void cacheLoginInfo(UserAccountDO userAccountDO, String jti) {
        String loginCacheKey = String.format(UserServiceRedisConstant.LOGIN_CACHE_KEY, jti);
        String loginCacheValue = userAccountDO.getId()
                + UserServiceRedisConstant.LOGIN_CACHE_VALUE_SEPARATOR
                + userAccountDO.getPermission();
        stringRedisTemplate.opsForValue().set(
                loginCacheKey,
                loginCacheValue,
                UserServiceRedisConstant.LOGIN_CACHE_TTL_HOURS,
                TimeUnit.HOURS
        );
    }

    private void cacheNickName(String accountId, String nickName) {
        stringRedisTemplate.opsForValue().set(
                String.format(UserServiceRedisConstant.USER_NICK_NAME_CACHE_KEY, accountId),
                nickName,
                UserServiceRedisConstant.LOGIN_CACHE_TTL_HOURS,
                TimeUnit.HOURS
        );
    }
}
