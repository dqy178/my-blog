package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录入参对象。
 *
 * <p>本项目不开放注册，账号由数据库种子数据初始化。</p>
 */
@Data
public class LoginDTO {

    /** 登录用户名，必填 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 登录密码，必填，长度 6~20 位 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20位之间")
    private String password;

    /**
     * 是否记住登录状态。
     *
     * <p>为 true 时 Redis 中 token 的 TTL 延长到 30 天，否则为 24 小时。</p>
     */
    private Boolean rememberMe;
}