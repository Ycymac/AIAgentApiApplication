package com.ycy.aiapplication.user.service.toolkit.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycy.aiapplication.user.service.common.context.UserContext;
import com.ycy.aiapplication.user.service.common.context.UserInfoDTO;
import com.ycy.aiapplication.user.service.dao.entity.UserAccountDO;
import com.ycy.aiapplication.user.service.dao.mapper.UserAccountDOMapper;
import com.ycy.aiapplication.user.service.toolkit.JWTUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JWTInterceptor implements HandlerInterceptor {

    private final JWTUtil jwtUtil;
    private final UserAccountDOMapper userAccountDOMapper;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");

        // 2. 检查请求头格式是否正确 (Bearer <token>)
        if (token == null || !token.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized: No valid token provided");
            return false; // 拦截请求，不再向下执行
        }
        //提取JWT字符串
        // 去掉 "Bearer " 前缀
        String jwt = token.substring(7);

        try{
            //验证JWT有效性
            //校验签名、过期时间等
            String accountId = jwtUtil.getAccountIdFromToken(jwt);
            //验证成功，将用户id存储到当前ThreadLocal当中
            LambdaQueryWrapper<UserAccountDO> queryWrapper = new LambdaQueryWrapper<UserAccountDO>()
                    .eq(UserAccountDO::getAccountId, accountId);
            UserAccountDO userAccountDO = userAccountDOMapper.selectOne(queryWrapper);

            if(ObjectUtil.isNull(userAccountDO)) return false;
            UserInfoDTO userInfoDTO = BeanUtil.toBean(userAccountDO, UserInfoDTO.class);
            UserContext.setUser(userInfoDTO);

            request.setAttribute("currentAccountId",accountId);

            return true;



        }catch (JWTVerificationException e){
            // 7. 验证失败（签名无效、过期等）
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized: Invalid token");
            return false; // 拦截请求
        }

    }
}
