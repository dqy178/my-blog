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

import java.io.IOException;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static class JwtFilterWrapper extends OncePerRequestFilter {
        private final JwtAuthenticationInterceptor delegate;

        JwtFilterWrapper(JwtAuthenticationInterceptor delegate) {
            this.delegate = delegate;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String uri = request.getRequestURI();
            if (uri == null) return true;
            
            // 剥离 context-path (/api)
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
                uri = uri.substring(contextPath.length());
            }
            
            // 注意：这里去掉了最后面的斜杠，防止 /v3/api-docs 精确匹配失败
            return uri.startsWith("/auth")
                    || uri.equals("/admin/user/forget-password")
                    || uri.startsWith("/front")
                    || uri.startsWith("/doc.html")
                    || uri.startsWith("/webjars")
                    || uri.startsWith("/swagger-resources")
                    || uri.startsWith("/v3/api-docs")
                    || uri.startsWith("/uploads")
                    || uri.equals("/favicon.ico");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            try {
                boolean ok = delegate.preHandle(request, response, new Object());
                if (!ok) return;
                chain.doFilter(request, response);
                delegate.afterCompletion(request, response, new Object(), null);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

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