package com.ycy.aiapplication.user.service.toolkit.interceptor;

import com.alibaba.fastjson2.JSON;
import com.ycy.aiapplication.framework.context.UserContext;
import com.ycy.aiapplication.framework.errorcode.BaseErrorCode;
import com.ycy.aiapplication.framework.web.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {

    private static final String ADMIN_PERMISSION_DENIED_MESSAGE = "当前接口需要管理员权限";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (UserContext.isManager()) {
            return true;
        }
        log.warn("Admin permission denied, userId={}, accountId={}, method={}, uri={}, remoteAddr={}",
                UserContext.getId(),
                UserContext.getAccountId(),
                request.getMethod(),
                buildRequestUri(request),
                request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Result<Void> result = new Result<Void>()
                .setCode(BaseErrorCode.CLIENT_ERROR.code())
                .setMessage(ADMIN_PERMISSION_DENIED_MESSAGE);
        response.getWriter().write(JSON.toJSONString(result));
        return false;
    }

    private String buildRequestUri(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + queryString;
    }
}
