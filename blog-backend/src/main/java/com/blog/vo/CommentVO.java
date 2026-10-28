package com.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论出参对象。
 *
 * <p>前端 {@code CommentVO} 类型与此字段一一对应。</p>
 */
@Data
public class CommentVO {

    /** 评论 ID */
    private Long id;

    /** 所属文章 ID */
    private Long articleId;

    /** 父评论 ID。null 表示顶级评论 */
    private Long parentId;

    /** 评论者用户 ID。游客评论为 null */
    private Long userId;

    /** 展示昵称 */
    private String nickname;

    /** 联系邮箱 */
    private String email;

    /** 评论内容（已做 XSS 净化） */
    private String content;

    /** 审核状态：0=待审核，1=通过，2=拒绝 */
    private Integer status;

    /** 是否管理员回复：1=是（前端显示「博主」标识） */
    private Integer isAdmin;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最后更新时间 */
    private LocalDateTime updateTime;

    /** 子回复列表，装配评论树时填充 */
    private List<CommentVO> children;

    /** 评论者昵称（预留给登录用户评论场景） */
    private String userNickname;

    /** 评论者头像（预留给登录用户评论场景） */
    private String userAvatar;
}