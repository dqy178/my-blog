package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("comment")
public class Comment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("article_id")
    private Long articleId;

    @TableField("parent_id")
    private Long parentId;

    @TableField("user_id")
    private Long userId;

    @TableField("nickname")
    private String nickname;

    @TableField("email")
    private String email;

    @TableField("website")
    private String website;

    @TableField("image_url")
    private String imageUrl;

    @TableField("avatar")
    private String avatar;

    @TableField("content")
    private String content;

    @TableField("ip")
    private String ip;

    @TableField("address")
    private String address;

    @TableField("status")
    private Integer status;

    @TableField("is_admin")
    private Integer isAdmin;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    @TableField(exist = false)
    private java.util.List<Comment> children;

    @TableField(exist = false)
    private String userNickname;

    @TableField(exist = false)
    private String userAvatar;
}