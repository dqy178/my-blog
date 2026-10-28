package com.blog.interceptor;

import com.blog.utils.JwtUtil;
import com.blog.utils.RedisUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 管理员路径鉴权的回归测试。
 *
 * <p>曾经的漏洞：管理员角色判定用 {@code request.getRequestURI().startsWith("/api/admin/")}，
 * 而该方法返回的是<b>未解码</b>的原始 URI，Spring 路由匹配用的却是<b>解码后</b>的路径。
 * 于是 {@code /api/%61dmin/articles} 会被路由到 AdminArticleController，
 * 却因为前缀不匹配而跳过角色校验 —— 普通用户可完整操作后台。
 * 修复后判定同时基于原始路径和解码后的路径。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationInterceptorAdminPathTest {

    private static final String SECRET = "blog-jwt-secret-key-please-keep-this-very-long-and-secure-2024-2025";

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private JwtAuthenticationInterceptor interceptor;

    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        when(redisUtil.hasKey(anyString())).thenReturn(true);
        when(jwtUtil.getTokenHead()).thenReturn("Bearer");
    }

    private String tokenFor(String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject("tester")
                .claim("userId", 1L)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(signingKey)
                .compact();
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(uri);
        req.setContextPath("/api");
        req.setMethod("GET");
        req.addHeader("Authorization", "Bearer " + tokenFor("ROLE_USER"));
        return req;
    }

    private void stubRole(String role) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(tokenFor(role)).getPayload();
        when(jwtUtil.parseToken(anyString())).thenReturn(claims);
        when(jwtUtil.validateToken(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("普通用户访问 /admin/** 应被拒绝(403)")
    void normalUser_adminPath_shouldBeForbidden() throws Exception {
        stubRole("ROLE_USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("/api/admin/articles/page"), response, new Object());

        assertFalse(allowed, "普通用户不应通过管理员路径校验");
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("普通用户用百分号编码绕过 /admin/** 也应被拒绝(403)")
    void normalUser_percentEncodedAdminPath_shouldBeForbidden() throws Exception {
        stubRole("ROLE_USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // %61 = 'a'，解码后是 /admin/articles/page
        boolean allowed = interceptor.preHandle(request("/api/%61dmin/articles/page"), response, new Object());

        assertFalse(allowed, "百分号编码不应绕过管理员路径校验");
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("管理员访问 /admin/** 应通过")
    void admin_adminPath_shouldPass() throws Exception {
        stubRole("ROLE_ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("/api/admin/articles/page"), response, new Object());

        assertTrue(allowed, "管理员应通过校验");
    }

    @Test
    @DisplayName("管理员用百分号编码访问 /admin/** 也应通过")
    void admin_percentEncodedAdminPath_shouldPass() throws Exception {
        stubRole("ROLE_ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("/api/%61dmin/articles/page"), response, new Object());

        assertTrue(allowed, "管理员应通过校验");
    }

    @Test
    @DisplayName("普通用户访问 /front/** 不应被管理员规则拦截")
    void normalUser_frontPath_shouldNotBeBlockedByAdminRule() throws Exception {
        stubRole("ROLE_USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // 前台接口在 Filter 层放行，MVC 拦截器只对 /admin/** 做角色校验，
        // 因此普通用户带 token 访问前台应通过
        boolean allowed = interceptor.preHandle(request("/api/front/articles/page"), response, new Object());

        assertTrue(allowed, "前台路径不应被管理员规则拦截");
    }
}
