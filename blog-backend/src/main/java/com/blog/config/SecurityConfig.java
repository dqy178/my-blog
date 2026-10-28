package com.blog.config;

import com.blog.interceptor.JwtAuthenticationInterceptor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置。
 *
 * <p>本项目不依赖 Spring Security 的默认授权机制（{@code authorizeHttpRequests} 全部
 * permitAll），真正的访问控制由 {@link JwtAuthenticationInterceptor} 与本类中的
 * {@link JwtFilterWrapper} 共同完成：</p>
 *
 * <ul>
 *   <li>{@link JwtFilterWrapper}：在过滤器层拦截非白名单路径，校验 token 是否存在、
 *       是否有效、是否仍在 Redis 中</li>
 *   <li>{@code JwtAuthenticationInterceptor}：在 MVC 层补充判定 {@code /admin/**}
 *       必须具备 {@code ROLE_ADMIN} 角色</li>
 * </ul>
 *
 * <p>两层都做路径判定（原始路径 + URL 解码后路径），是为了防止
 * {@code %61dmin} 这类百分号编码绕过角色校验。</p>
 *
 * <p>会话策略为无状态（STATELESS）：登录态完全由 JWT + Redis 承载，
 * 服务端不保存 HttpSession，因此可以水平扩容。</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    /** 密码编码器：BCrypt，自带随机盐，同一密码每次加密结果不同 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT 校验过滤器。
     *
     * <p>包装 {@link JwtAuthenticationInterceptor}，把原本面向 MVC 的
     * {@code HandlerInterceptor} 适配成 {@link OncePerRequestFilter}，
     * 以便在请求进入 Controller 之前就完成 token 校验。</p>
     */
    private static class JwtFilterWrapper extends OncePerRequestFilter {
        private final JwtAuthenticationInterceptor delegate;

        JwtFilterWrapper(JwtAuthenticationInterceptor delegate) {
            this.delegate = delegate;
        }

        /**
         * 判断当前请求是否应当跳过 JWT 校验。
         *
         * <p>白名单路径（登录、验证码、前台接口、API 文档、静态上传资源）直接放行。</p>
         */
        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String uri = request.getRequestURI();
            if (uri == null) return true;

            // 剥离 context-path (/api)
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
                uri = uri.substring(contextPath.length());
            }

            if (isWhitelisted(uri)) {
                return true;
            }
            // 原始路径未命中时，用解码后的路径再判一次。
            // requestURI 是未解码的原始值，而 Spring 路由按解码后的路径匹配，
            // 不解码会导致 %66ront 这类编码路径的放行判断与实际路由不一致。
            try {
                return isWhitelisted(UriUtils.decode(uri, StandardCharsets.UTF_8));
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * 白名单路径判定。
         *
         * <p>全部使用 {@code startsWith} 而非精确匹配：例如 {@code /front} 覆盖
         * {@code /front/articles/page} 等所有前台接口。这里刻意不写成
         * {@code /front/}（带尾斜杠），否则 {@code /front} 本身反而匹配不上。</p>
         */
        private boolean isWhitelisted(String path) {
            if (path == null) return false;
            // 注意：这里去掉了最后面的斜杠，防止 /v3/api-docs 精确匹配失败
            return path.startsWith("/auth")
                    || path.equals("/admin/user/forget-password")
                    || path.startsWith("/front")
                    || path.startsWith("/doc.html")
                    || path.startsWith("/webjars")
                    || path.startsWith("/swagger-resources")
                    || path.startsWith("/v3/api-docs")
                    || path.startsWith("/uploads")
                    || path.equals("/favicon.ico");
        }

        /**
         * 执行 JWT 校验并放行请求。
         *
         * <p>校验失败时 {@code preHandle} 已提前写入 401/403 响应并返回 false，
         * 此时不能继续调用 {@code chain.doFilter}。</p>
         */
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            try {
                boolean ok = delegate.preHandle(request, response, new Object());
                if (!ok) {
                    // 校验未通过时 preHandle 已提前返回，afterCompletion 不会被调用，
                    // 必须在这里清理 ThreadLocal，否则 Tomcat 复用线程时身份会串到下一个请求。
                    clearUserId();
                    return;
                }
                chain.doFilter(request, response);
            } catch (Exception e) {
                throw new ServletException(e);
            } finally {
                clearUserId();
            }
        }

        /**
         * 清理 ThreadLocal 中保存的用户身份。
         *
         * <p>包一层 try/catch，确保清理失败不会影响请求本身的处理结果。</p>
         */
        private void clearUserId() {
            try {
                delegate.afterCompletion(null, null, new Object(), null);
            } catch (Exception ignored) {
                // 清理失败不影响请求本身
            }
        }
    }

    /**
     * 构建过滤器链。
     *
     * <p>要点：</p>
     * <ul>
     *   <li>关闭 CSRF：无状态 + Authorization 头鉴权，不存在表单 CSRF 面</li>
     *   <li>会话策略设为 STATELESS：不创建 HttpSession</li>
     *   <li>关闭 httpBasic 与 formLogin：认证完全由自定义 JWT 过滤器承担</li>
     *   <li>JWT 过滤器挂在 SecurityContextHolderFilter 之后，
     *       保证 Spring Security 的上下文初始化已完成</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtFilterWrapper jwtFilter = new JwtFilterWrapper(jwtAuthenticationInterceptor);
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .addFilterAfter(jwtFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class);
        return http.build();
    }
}