package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 友链实体，对应数据库表 {@code friend_link}。
 *
 * <p>前台只展示 {@code status = 1} 的友链；后台列表返回全部（含已隐藏）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("friend_link")
public class FriendLink implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 友链名称 */
    @TableField("name")
    private String name;

    /** 友链地址 */
    @TableField("url")
    private String url;

    /** 友链头像 URL */
    @TableField("avatar")
    private String avatar;

    /** 友链描述 */
    @TableField("description")
    private String description;

    /** 排序值，越小越靠前 */
    @TableField("sort")
    private Integer sort;

    /** 显示状态：1=显示，0=隐藏 */
    @TableField("status")
    private Integer status;

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
}