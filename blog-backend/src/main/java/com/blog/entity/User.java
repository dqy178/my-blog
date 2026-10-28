package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库表 {@code user}。
 *
 * <p>本项目为单用户（单博主）模型，不开放注册，账号由数据库种子数据初始化。</p>
 *
 * <p>{@code password} 与 {@code securityAnswer1/2} 均以 BCrypt 密文存储，
 * 任何情况下都不会出现在 VO 中。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("user")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录用户名，唯一 */
    @TableField("username")
    private String username;

    /** 登录密码，BCrypt 密文 */
    @TableField("password")
    private String password;

    /** 昵称，前台评论区展示用 */
    @TableField("nickname")
    private String nickname;

    /** 邮箱，唯一 */
    @TableField("email")
    private String email;

    /** 头像 URL */
    @TableField("avatar")
    private String avatar;

    /** 个人简介 */
    @TableField("bio")
    private String bio;

    /** 座右铭 */
    @TableField("motto")
    private String motto;

    /** 角色：ROLE_ADMIN（管理员）/ ROLE_USER（普通用户） */
    @TableField("role")
    private String role;

    /** 账号状态：1=正常，0=已禁用 */
    @TableField("status")
    private Integer status;

    /** 最后登录时间 */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 安全问题 1 的答案，BCrypt 密文。用于密保找回密码 */
    @TableField("security_answer1")
    private String securityAnswer1;

    /** 安全问题 2 的答案，BCrypt 密文。用于密保找回密码 */
    @TableField("security_answer2")
    private String securityAnswer2;

    /** 逻辑删除标记：0=未删除，1=已删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}