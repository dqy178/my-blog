package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论实体，对应数据库表 {@code comment}。
 *
 * <p>评论采用两级结构：{@code parentId} 为 NULL 表示顶级评论，
 * 否则表示对某条评论的回复。删除父评论时会递归物理删除其所有子回复。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("comment")
public class Comment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属文章 ID */
    @TableField("article_id")
    private Long articleId;

    /** 父评论 ID。NULL 表示顶级评论，否则为对某条评论的回复 */
    @TableField("parent_id")
    private Long parentId;

    /** 评论者用户 ID。游客评论为 NULL */
    @TableField("user_id")
    private Long userId;

    /** 展示昵称。游客填写的昵称或管理员昵称 */
    @TableField("nickname")
    private String nickname;

    /** 联系邮箱（仅作展示，不对外暴露） */
    @TableField("email")
    private String email;

    /** 评论内容，入库前经 HtmlSanitizer 白名单净化 */
    @TableField("content")
    private String content;

    /** 审核状态：0=待审核，1=已通过，2=已拒绝 */
    @TableField("status")
    private Integer status;

    /** 是否管理员回复：1=是（前端显示「博主」标识），0=否 */
    @TableField("is_admin")
    private Integer isAdmin;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0=未删除，1=已删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 子回复列表（非数据库字段，装配评论树时填充） */
    @TableField(exist = false)
    private java.util.List<Comment> children;

    /** 评论者昵称（非数据库字段，预留给登录用户评论场景） */
    @TableField(exist = false)
    private String userNickname;

    /** 评论者头像（非数据库字段，预留给登录用户评论场景） */
    @TableField(exist = false)
    private String userAvatar;
}