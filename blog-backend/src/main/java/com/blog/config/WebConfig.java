package com.blog.config;

import com.blog.interceptor.JwtAuthenticationInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：拦截器注册、静态资源映射、跨域配置。
 *
 * <p>本类与 {@link SecurityConfig} 共同构成鉴权体系：
 * {@link SecurityConfig} 的过滤器负责「有没有登录」，
 * 本类注册的 {@link JwtAuthenticationInterceptor} 负责「能不能访问后台」。</p>
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    @Value("${file.upload.path}")
    private String uploadPath;

    /**
     * 允许跨域的来源白名单（逗号分隔）。
     * 生产环境通过 CORS_ALLOWED_ORIGINS 环境变量指定实际域名。
     */
    @Value("${cors.allowed-origins:http://localhost:[*],http://127.0.0.1:[*]}")
    private String corsAllowedOrigins;

    public WebConfig(JwtAuthenticationInterceptor jwtAuthenticationInterceptor) {
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
        log.info("[WebConfig] 初始化，注册 JWT 拦截器");
    }

    /**
     * 注册 JWT 拦截器。
     *
     * <p>拦截器的路径规则不含 context-path：MVC 拦截器拿到的路径已经去掉了
     * {@code /api} 前缀，因此 {@code "/api/**"} 这一条实际匹配不到任何请求，
     * 真正生效的是 {@code "/front/comments"} —— 它把前台提交评论单独纳入校验，
     * 且允许匿名（游客可评论，带 token 时可识别管理员身份）。</p>
     *
     * <p>其余 {@code /admin/**} 接口由 {@code SecurityConfig} 的 JWT Filter 负责校验，
     * 因此这里排除掉，避免重复鉴权。</p>
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("[WebConfig] 即将注册 JWT 拦截器，jwtAuthenticationInterceptor 实例 = {}", jwtAuthenticationInterceptor);
        registry.addInterceptor(jwtAuthenticationInterceptor)
                // 注意：MVC 拦截器的路径不含 context-path(/api)，所以 "/api/**" 实际匹配不到请求，
                // 真正生效的是 "/front/comments"。
                .addPathPatterns("/api/**", "/front/comments")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/front/**",
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/uploads/**"
                );
        log.info("[WebConfig] JWT 拦截器已注册");
    }

    /**
     * 注册上传文件的静态资源映射：{@code /uploads/**} → 本地目录。
     *
     * <p>目录来自配置项 {@code file.upload.path}，本地与容器各自适配，
     * 避免把某台机器的绝对路径写死在代码里。结尾补斜杠并统一用 {@code file:} 前缀，
     * 保证拼接出的路径能被正确解析。</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 从配置读 upload.path，本地/容器各自适配；结尾补斜杠，统一用 file: 前缀
        String location = uploadPath;
        if (!location.endsWith("/") && !location.endsWith("\\")) {
            location += "/";
        }
        log.info("[WebConfig] 静态资源映射: /uploads/** -> file:{}", location);
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + location);
    }

    /**
     * 配置跨域规则。
     *
     * <p>来源白名单来自配置项 {@code cors.allowed-origins}，生产环境通过
     * {@code CORS_ALLOWED_ORIGINS} 指定实际域名。</p>
     *
     * <p>为什么不用通配符：之前是 {@code allowedOriginPatterns("*")} 搭配
     * {@code allowCredentials(true)}，会回显任意 Origin，任意站点都能发起带凭据的
     * 跨域请求并读到响应；当前 token 存在 sessionStorage（不随请求自动携带）所以
     * 危害有限，但一旦改用 Cookie 鉴权就会立刻变成账号接管。</p>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 白名单按逗号分隔配置，例如：http://a.com,http://b.com
        String[] origins = corsAllowedOrigins.split(",");
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}