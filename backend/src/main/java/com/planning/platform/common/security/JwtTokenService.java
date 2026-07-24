package com.planning.platform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String ACCESS_PREFIX = "auth:access:";
    private static final String REFRESH_PREFIX = "auth:refresh:";

    private final SecurityProperties properties;
    private final StringRedisTemplate redisTemplate;

    public String createAccessToken(AuthUser user) {
        String tokenId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.getAccessTokenMinutes()));
        String token = buildToken(tokenId, user, "access", now, expiresAt);
        redisTemplate.opsForValue().set(ACCESS_PREFIX + tokenId, String.valueOf(user.userId()),
                Duration.ofMinutes(properties.getAccessTokenMinutes()));
        return token;
    }

    public String createRefreshToken(AuthUser user) {
        String tokenId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofDays(properties.getRefreshTokenDays()));
        String token = buildToken(tokenId, user, "refresh", now, expiresAt);
        redisTemplate.opsForValue().set(REFRESH_PREFIX + tokenId, String.valueOf(user.userId()),
                Duration.ofDays(properties.getRefreshTokenDays()));
        return token;
    }

    public AuthUser parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        if (!"access".equals(claims.get("tokenType", String.class))) {
            throw new IllegalArgumentException("Token type mismatch");
        }
        requireSession(ACCESS_PREFIX + claims.getId());
        return toAuthUser(claims);
    }

    public AuthUser parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        if (!"refresh".equals(claims.get("tokenType", String.class))) {
            throw new IllegalArgumentException("Token type mismatch");
        }
        requireSession(REFRESH_PREFIX + claims.getId());
        return toAuthUser(claims);
    }

    public void revokeAccessToken(String token) {
        Claims claims = parseClaims(token);
        redisTemplate.delete(ACCESS_PREFIX + claims.getId());
    }

    private String buildToken(String tokenId, AuthUser user, String tokenType, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .id(tokenId)
                .subject(String.valueOf(user.userId()))
                .claim("tokenType", tokenType)
                .claim("username", user.username())
                .claim("realName", user.realName())
                .claim("deptId", user.deptId())
                .claim("groupId", user.groupId())
                .claim("forceChangePassword", user.forceChangePassword())
                .claim("roles", user.roles())
                .claim("permissions", user.permissions())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey())
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void requireSession(String key) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new IllegalArgumentException("Token session expired");
        }
    }

    @SuppressWarnings("unchecked")
    private AuthUser toAuthUser(Claims claims) {
        Object deptIdValue = claims.get("deptId");
        Long deptId = deptIdValue == null ? null : Long.valueOf(String.valueOf(deptIdValue));
        Object groupIdValue = claims.get("groupId");
        Long groupId = groupIdValue == null ? null : Long.valueOf(String.valueOf(groupIdValue));
        return new AuthUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("realName", String.class),
                deptId,
                groupId,
                claims.get("forceChangePassword", Boolean.class),
                (List<String>) claims.getOrDefault("roles", List.of()),
                (List<String>) claims.getOrDefault("permissions", List.of())
        );
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }
}
