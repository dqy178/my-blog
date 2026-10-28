package com.blog.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 强制所有 JSON / String 响应以 UTF-8 编码返回，
 * 避免某些 Tomcat + Jackson 组合下默认使用 ISO-8859-1 导致中文乱码。
 */
@Configuration
public class JacksonConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
        converters.stream()
                .filter(StringHttpMessageConverter.class::isInstance)
                .map(StringHttpMessageConverter.class::cast)
                .forEach(c -> c.setDefaultCharset(StandardCharsets.UTF_8));

        converters.stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .forEach(c -> {
                    c.setDefaultCharset(StandardCharsets.UTF_8);
                    // 在某些 Spring Boot 版本下，需要把 UTF-8 MediaType 显式插入到支持列表
                    c.setSupportedMediaTypes(java.util.List.of(
                            MediaType.APPLICATION_JSON,
                            new MediaType("application", "*+json", StandardCharsets.UTF_8)));
                });
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 忽略请求体中的未知字段。
        // 这里自定义了 ObjectMapper，会覆盖 Spring Boot 的 Jackson 自动配置，
        // 因此 application.yml 里的 spring.jackson.deserialization.fail-on-unknown-properties
        // 不会生效，必须在此显式设置；否则前端多传一个已废弃字段就会直接 500。
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
