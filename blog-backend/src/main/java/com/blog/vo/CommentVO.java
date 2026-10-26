package com.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentVO {

    private Long id;
    private Long articleId;
    private Long parentId;
    private Long userId;
    private String nickname;
    private String email;
    private String website;
    private String imageUrl;
    private String avatar;
    private String content;
    private String ip;
    private String address;
    private Integer status;
    private Integer isAdmin;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<CommentVO> children;
    private String userNickname;
    private String userAvatar;
}