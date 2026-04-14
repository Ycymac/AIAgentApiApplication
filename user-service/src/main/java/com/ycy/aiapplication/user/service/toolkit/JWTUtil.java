package com.ycy.aiapplication.user.service.toolkit;


import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;


/**
 * JWT令牌工具类
 */
@Component
public class JWTUtil {
    /**
     * 配置文件密钥
     */
    @Value("${app.jwt.secret:AiApplicationSecretKeyForHarmonyOsApp2026}")
    private String SECRET;

    /**
     * jwt过期时间
     */
    @Value("${app.jwt.expiration:86400000}")
    private Long EXPIRATION_TIME;

    /**
     * 生成JWT Token
     * @param accountId 账户id
     * @return 返回JWT Token
     */
    public String generateToken(String accountId){
        Date now = new Date();
        Date expriyDate = new Date(now.getTime() + EXPIRATION_TIME);

        return JWT.create()
                .withSubject(accountId)
                .withIssuedAt(now)//签发时间
                .withExpiresAt(expriyDate)//过期时间
                .sign(Algorithm.HMAC256(SECRET));//使用的算法和签名
    }

    /**
     *验证JWT token 并返回解码后的JWT对象
     * @param token JWT字符串
     * @return 解码后的对象
     * @throws JWTVerificationException 验证失败抛出异常
     */
    public DecodedJWT verifyToken(String token)throws JWTVerificationException{
        Algorithm algorithm = Algorithm.HMAC256(SECRET);
        JWTVerifier verifier = JWT.require(algorithm).build();//创建验证器
        return verifier.verify(token);
    }

    public String getAccountIdFromToken(String token){
        DecodedJWT decodedJWT = verifyToken(token);
        return decodedJWT.getSubject();
    }



}
