package com.ycy.aiapplication.user.service.toolkit.interceptor;

import cn.hutool.core.util.StrUtil;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.context.UserInfoDTO;
import com.ycy.aiapplication.user.service.common.constant.UserServiceRedisConstant;
import com.ycy.aiapplication.user.service.toolkit.JWTUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Date;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class JWTInterceptor implements HandlerInterceptor {

    private final JWTUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            writeUnauthorized(response, "Unauthorized: No valid token provided");
            return false;
        }

        String jwt = token.substring(7);
        try {
            DecodedJWT decodedJWT = jwtUtil.verifyToken(jwt);
            Long userId = jwtUtil.getUserId(decodedJWT);
            String accountId = jwtUtil.getAccountId(decodedJWT);
            String jti = jwtUtil.getJti(decodedJWT);
            Date expiresAt = decodedJWT.getExpiresAt();
            if (userId == null || StrUtil.isBlank(accountId) || StrUtil.isBlank(jti) || expiresAt == null || expiresAt.before(new Date())) {
                writeUnauthorized(response, "Unauthorized: Invalid token");
                return false;
            }

            String loginCacheKey = String.format(UserServiceRedisConstant.LOGIN_CACHE_KEY, jti);
            String loginCacheValue = stringRedisTemplate.opsForValue().get(loginCacheKey);
            if (StrUtil.isBlank(loginCacheValue)) {
                writeUnauthorized(response, "Unauthorized: Login status expired");
                return false;
            }

            String[] cacheValues = loginCacheValue.split("\\|");
            if (cacheValues.length != 2) {
                writeUnauthorized(response, "Unauthorized: Invalid login cache");
                return false;
            }

            Long cachedUserId = Long.valueOf(cacheValues[0]);
            Integer permission = Integer.valueOf(cacheValues[1]);
            if (!Objects.equals(userId, cachedUserId)) {
                writeUnauthorized(response, "Unauthorized: Token mismatch");
                return false;
            }

            UserContext.setUser(UserInfoDTO.builder()
                    .id(userId)
                    .accountId(accountId)
                    .permission(permission)
                    .jti(jti)
                    .loginExpireTime(expiresAt.getTime())
                    .build());
            request.setAttribute("currentAccountId", accountId);
            return true;
        } catch (JWTVerificationException | IllegalArgumentException e) {
            writeUnauthorized(response, "Unauthorized: Invalid token");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.removeUser();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(message);
    }
}
