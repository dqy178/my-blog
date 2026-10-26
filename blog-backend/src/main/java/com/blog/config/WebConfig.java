package com.blog.config;

import com.blog.interceptor.JwtAuthenticationInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    @Value("${file.upload.path}")
    private String uploadPath;

    public WebConfig(JwtAuthenticationInterceptor jwtAuthenticationInterceptor) {
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
        log.info("[WebConfig] 初始化，注册 JWT 拦截器");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("[WebConfig] 即将注册 JWT 拦截器，jwtAuthenticationInterceptor 实例 = {}", jwtAuthenticationInterceptor);
        registry.addInterceptor(jwtAuthenticationInterceptor)
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

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}