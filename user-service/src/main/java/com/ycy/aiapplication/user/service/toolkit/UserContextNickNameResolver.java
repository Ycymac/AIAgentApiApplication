package com.ycy.aiapplication.user.service.toolkit;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.context.UserNickNameResolver;
import com.ycy.aiapplication.user.service.common.constant.UserServiceRedisConstant;
import com.ycy.aiapplication.user.service.dao.entity.UserAccountDO;
import com.ycy.aiapplication.user.service.dao.mapper.UserAccountDOMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class UserContextNickNameResolver implements UserNickNameResolver {

    private final StringRedisTemplate stringRedisTemplate;
    private final UserAccountDOMapper userAccountDOMapper;

    @PostConstruct
    public void register() {
        UserContext.setUserNickNameResolver(this);
    }

    @Override
    public String getNickName(String accountId) {
        if (StrUtil.isBlank(accountId)) {
            return null;
        }
        String nickNameCacheKey = String.format(UserServiceRedisConstant.USER_NICK_NAME_CACHE_KEY, accountId);
        String nickName = stringRedisTemplate.opsForValue().get(nickNameCacheKey);
        if (StrUtil.isNotBlank(nickName)) {
            return nickName;
        }

        UserAccountDO userAccountDO = userAccountDOMapper.selectOne(new LambdaQueryWrapper<UserAccountDO>()
                .eq(UserAccountDO::getAccountId, accountId));
        if (userAccountDO == null || StrUtil.isBlank(userAccountDO.getNickName())) {
            return null;
        }

        stringRedisTemplate.opsForValue().set(
                nickNameCacheKey,
                userAccountDO.getNickName(),
                UserServiceRedisConstant.LOGIN_CACHE_TTL_HOURS,
                TimeUnit.HOURS
        );
        return userAccountDO.getNickName();
    }
}
