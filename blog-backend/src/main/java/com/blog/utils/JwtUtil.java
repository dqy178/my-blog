package com.blog.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类。
 *
 * <p>负责 Token 的签发与解析。Token 中携带 {@code userId}、{@code role} 三个信息：
 * subject 为用户名，claim 中存放用户 ID 与角色。</p>
 *
 * <p>密钥长度由 {@link #init()} 在启动时强校验，不足直接抛异常终止启动，
 * 避免用弱密钥签发出可被伪造的 Token。</p>
 */
@Slf4j
@Component
public class JwtUtil {

    /** 签名密钥。生产环境通过 JWT_SECRET 环境变量注入 */
    @Value("${jwt.secret}")
    private String secret;

    /** Token 有效期（毫秒），默认 24 小时 */
    @Value("${jwt.expiration}")
    private Long expiration;

    /** Token 请求头名称，固定为 Authorization */
    @Value("${jwt.token-head}")
    private String tokenHead;

    /** 派生出的签名密钥，启动时初始化 */
    private SecretKey signingKey;

    /**
     * 启动时校验密钥长度并初始化签名密钥。
     *
     * <p>JJWT 0.12 对 HMAC 密钥长度有强校验：HS256 需 ≥32 字节，HS512 需 ≥64 字节。
     * 这里按 32 字节作为下限，密钥不足时直接抛异常终止启动。</p>
     */
    @PostConstruct
    public void init() {
        // JJWT 0.12 强校验 HMAC 密钥长度：HS256 ≥32 字节；HS512 ≥64 字节
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret 长度不足 32 字节，无法用于 HS256 签名");
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * 签发 Token。
     *
     * @param userId   用户 ID，写入 claim
     * @param username 用户名，写入 subject
     * @param role     角色，写入 claim（后台路径鉴权依赖它）
     * @return 签名后的 JWT 字符串
     */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 解析并验签 Token。
     *
     * @param token JWT 字符串
     * @return 解析出的载荷；签名无效、已过期或格式非法时返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Token 中取出用户 ID。
     *
     * @return 用户 ID，解析失败或 claim 缺失时返回 null
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            // JJWT 0.12 数字 claim 默认反序列化为 Integer / Long 都可能，先取 Number 强转
            Object v = claims.get("userId");
            if (v instanceof Number n) return n.longValue();
        }
        return null;
    }

    /**
     * 从 Token 中取出用户名（subject）。
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.getSubject();
    }

    /**
     * 从 Token 中取出角色。
     *
     * <p>后台路径鉴权依赖该值，与 {@code ROLE_ADMIN} 比较。</p>
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        Object role = claims.get("role");
        return role == null ? null : role.toString();
    }

    /**
     * 校验 Token 是否有效（能验签且未过期）。
     *
     * @return 有效返回 true，否则返回 false
     */
    public boolean validateToken(String token) {
        Claims claims = parseToken(token);
        return claims != null && claims.getExpiration() != null
                && claims.getExpiration().after(new Date());
    }

    /**
     * 获取 Token 请求头前缀（如 {@code Bearer}）。
     *
     * <p>客户端发送 {@code Authorization: Bearer <token>}，
     * 解析时需要用该前缀做剥离。</p>
     */
    public String getTokenHead() {
        return tokenHead;
    }
}