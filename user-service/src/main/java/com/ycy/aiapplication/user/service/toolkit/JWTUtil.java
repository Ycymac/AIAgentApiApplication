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

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ACCOUNT_ID = "accountId";

    @Value("${app.jwt.secret:AiApplicationSecretKeyForHarmonyOsApp2026}")
    private String secret;

    @Value("${app.jwt.expiration:36000000}")
    private Long expirationTime;

    public String generateToken(Long userId, String accountId, String jti) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);
        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withJWTId(jti)
                .withClaim(CLAIM_USER_ID, userId)
                .withClaim(CLAIM_ACCOUNT_ID, accountId)
                .withIssuedAt(now)
                .withExpiresAt(expiryDate)
                .sign(Algorithm.HMAC256(secret));
    }

    public DecodedJWT verifyToken(String token) throws JWTVerificationException {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        JWTVerifier verifier = JWT.require(algorithm).build();
        return verifier.verify(token);
    }

    public Long getUserId(DecodedJWT decodedJWT) {
        Long userId = decodedJWT.getClaim(CLAIM_USER_ID).asLong();
        if (userId != null) {
            return userId;
        }
        return Long.valueOf(decodedJWT.getSubject());
    }

    public Long getUserIdFromToken(String token) {
        return getUserId(verifyToken(token));
    }

    public String getAccountId(DecodedJWT decodedJWT) {
        return decodedJWT.getClaim(CLAIM_ACCOUNT_ID).asString();
    }

    public String getAccountIdFromToken(String token) {
        return getAccountId(verifyToken(token));
    }

    public String getJti(DecodedJWT decodedJWT) {
        return decodedJWT.getId();
    }

    public Long getExpirationTime() {
        return expirationTime;
    }
}
