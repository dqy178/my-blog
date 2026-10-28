package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论入参对象。
 *
 * <p>用于「游客/用户发表评论」与「管理员回复」两个场景：
 * 管理员回复时 {@code parentId} 由服务端从路径参数写入，
 * 昵称固定为「管理员」。</p>
 */
@Data
public class CommentDTO {

    /** 主键 ID。新建时会被忽略（主键由数据库生成） */
    private Long id;

    /** 所属文章 ID，必填 */
    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    /** 父评论 ID。null 表示顶级评论（开楼），否则为回复某条评论 */
    private Long parentId;

    /** 评论内容，必填，长度上限 1000 字符 */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容长度不能超过1000字符")
    private String content;

    /** 昵称。游客必填；登录用户由服务端自动填充 */
    private String nickname;

    /** 邮箱。游客必填；登录用户由服务端自动填充 */
    private String email;

    /** 游客评论必填：验证码 ID，由 GET /api/auth/captcha 获取 */
    private String captchaId;

    /** 游客评论必填：验证码答案 */
    private String captcha;
}