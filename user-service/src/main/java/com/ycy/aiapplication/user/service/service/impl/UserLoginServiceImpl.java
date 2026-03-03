package com.ycy.aiapplication.user.service.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycy.aiapplication.framework.errorcode.BaseErrorCode;
import com.ycy.aiapplication.framework.exception.ClientException;
import com.ycy.aiapplication.framework.web.Result;
import com.ycy.aiapplication.framework.web.Results;
import com.ycy.aiapplication.user.service.common.constant.UserServiceRedisConstant;
import com.ycy.aiapplication.user.service.dao.entity.UserAccountDO;
import com.ycy.aiapplication.user.service.dao.mapper.UserAccountDOMapper;
import com.ycy.aiapplication.user.service.dto.resp.LoginRespDTO;
import com.ycy.aiapplication.user.service.service.UserLoginService;
import com.ycy.aiapplication.user.service.toolkit.JWTUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoginServiceImpl implements UserLoginService {

    private  final UserAccountDOMapper userAccountDOMapper;
    private final JWTUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signUpNewAccount(String accountId, String password, String nickName) {
        if(StrUtil.isEmpty(accountId)||StrUtil.isEmpty(password)||StrUtil.isEmpty(nickName)){
            log.error("用户存在信息为空，accountId:{}is empty,password{}is empty,nickName{}is empty",StrUtil.isEmpty(accountId),StrUtil.isEmpty(password),StrUtil.isEmpty(nickName));
            throw new ClientException("用户注册信息不完整，请检查！");
        }
        //这里我们查询redis是否有相同accountId
        String cacheKey = String.format(UserServiceRedisConstant.USER_ACCOUNT_CACHE_KEY, accountId);
        Boolean exists = stringRedisTemplate.hasKey(cacheKey);
        if (Boolean.TRUE.equals(exists)) {
            log.error("用户使用的id已经存在！账户id：{}",accountId);
            throw new ClientException("注册用户失败！当前用户id已经存在！");
        }
        //这里只是实现简单的用户注册逻辑，后续进行迭代优化

        //这里我们只是用String进行accountId和nickname存储，因为key当中有了对应的账户id.单步redis操作，也无需lua脚本
        //因为这里暂时只有一个昵称需要我们存储，只使用String类型存储，后续有例如照片等信息需要存储再进行修改
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
