package com.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户出参对象。
 *
 * <p>刻意不包含 {@code password}、{@code securityAnswer1/2} 等敏感字段，
 * 从 VO 层面杜绝密码与密保答案泄露。</p>
 */
@Data
public class UserVO {

    /** 用户 ID */
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 头像 URL */
    private String avatar;

    /** 个人简介 */
    private String bio;

    /** 座右铭 */
    private String motto;

    /** 角色：ROLE_ADMIN / ROLE_USER */
    private String role;

    /** 账号状态：1=正常，0=已禁用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 文章数（口径：仅统计已发布文章） */
    private Long articleCount;

    /** 评论数（口径：全站已审核通过的评论） */
    private Long commentCount;

    /** 总浏览量（口径：全站未删除文章的浏览量之和） */
    private Long viewCount;
}