package com.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendLinkVO {

    private Long id;
    private String name;
    private String url;
    private String avatar;
    private String description;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}