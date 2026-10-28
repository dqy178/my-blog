package com.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 友链出参对象。
 */
@Data
public class FriendLinkVO {

    /** 友链 ID */
    private Long id;

    /** 友链名称 */
    private String name;

    /** 友链地址 */
    private String url;

    /** 友链头像 URL */
    private String avatar;

    /** 友链描述 */
    private String description;

    /** 排序值 */
    private Integer sort;

    /** 显示状态：1=显示，0=隐藏 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最后更新时间 */
    private LocalDateTime updateTime;
}