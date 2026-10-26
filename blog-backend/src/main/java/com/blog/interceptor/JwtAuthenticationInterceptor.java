package com.blog.interceptor;

import com.blog.utils.JwtUtil;
import com.blog.utils.RedisUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    private static final ThreadLocal<Long> USER_ID_THREAD_LOCAL = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        log.info("[JwtAuth] preHandle entered, uri={} method={}", request.getRequestURI(), request.getMethod());
        String token = getTokenFromRequest(request);
        log.info("[JwtAuth] uri={} method={} token={}", request.getRequestURI(), request.getMethod(), token);
        if (!StringUtils.hasText(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或token已过期\",\"data\":null}");
            return false;
        }

        if (!jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"token无效或已过期\",\"data\":null}");
            return false;
        }

        String redisKey = "token:" + token;
        if (!Boolean.TRUE.equals(redisUtil.hasKey(redisKey))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"token已失效，请重新登录\",\"data\":null}");
            return false;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId != null) {
            USER_ID_THREAD_LOCAL.set(userId);
            // 仅在 token 接近过期（<1h）时才续期；否则保留登录时设置的 rememberMe TTL
            Claims claims = jwtUtil.parseToken(token);
            if (claims != null) {
                long leftMs = claims.getExpiration().getTime() - System.currentTimeMillis();
                if (leftMs < java.util.concurrent.TimeUnit.HOURS.toMillis(1)) {
                    redisUtil.set(redisKey, userId.toString(), 24, java.util.concurrent.TimeUnit.HOURS);
                }
                // /api/admin/** 路径强制要求 ADMIN 角色
                String uri = request.getRequestURI();
                if (uri != null && uri.startsWith("/api/admin/")) {
                    String role = claims.get("role", String.class);
                    if (!"ROLE_ADMIN".equals(role)) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":403,\"message\":\"没有权限访问\",\"data\":null}");
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        USER_ID_THREAD_LOCAL.remove();
    }

    public static Long getCurrentUserId() {
        return USER_ID_THREAD_LOCAL.get();
    }

    public static void setCurrentUserId(Long userId) {
        USER_ID_THREAD_LOCAL.set(userId);
    }

    public static void clearCurrentUserId() {
        USER_ID_THREAD_LOCAL.remove();
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        // 从标准 Authorization 头读取，去除 "Bearer " 前缀
        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader)) return null;
        String prefix = jwtUtil.getTokenHead() + " ";
        if (authHeader.startsWith(prefix)) {
            return authHeader.substring(prefix.length()).trim();
        }
        return null;
    }
}