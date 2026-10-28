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
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    private static final ThreadLocal<Long> USER_ID_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 允许匿名访问的接口：没有 token 时放行（游客身份），
     * 带了 token 则照常校验，以便识别管理员身份。
     * 用于前台提交评论——游客可评论，管理员登录后评论会被标记为管理员。
     */
    private boolean isOptionalAuth(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        // requestURI 含 context-path(/api)，剥离后再比对
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String path = uri.startsWith("/api") ? uri.substring("/api".length()) : uri;
        return "/front/comments".equals(path);
    }

    /**
     * 判定请求是否落在后台管理路径（/admin/**），需要 ROLE_ADMIN。
     *
     * <p>必须基于「解码后」的路径判定：Spring 路由匹配用的是解码后的路径，
     * 而 {@code request.getRequestURI()} 返回的是未解码的原始值。
     * 若直接用原始 URI 做前缀判断，攻击者可用 %61 之类的百分号编码
     * 让 {@code /api/%61dmin/articles} 同时满足「路由命中后台」与「前缀不匹配」，
     * 从而绕过角色校验。因此这里对原始路径和解码后路径都做一次判定。</p>
     */
    private boolean isAdminPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        String path = uri;
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (isAdminPrefix(path)) {
            return true;
        }
        // 原始路径未命中时，用解码后的路径再判一次，防止百分号编码绕过
        try {
            return isAdminPrefix(UriUtils.decode(path, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    /** 忽略大小写判断 /admin/ 前缀：路由匹配规则变化时也保持安全 */
    private boolean isAdminPrefix(String path) {
        return path != null && path.toLowerCase().startsWith("/admin/");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = getTokenFromRequest(request);
        if (!StringUtils.hasText(token)) {
            if (isOptionalAuth(request)) {
                // 游客提交评论，放行且不设置 ThreadLocal（评论按访客处理）
                return true;
            }
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
                // /admin/** 路径强制要求 ADMIN 角色
                if (isAdminPath(request)) {
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