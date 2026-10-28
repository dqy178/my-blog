package com.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签出参对象。
 */
@Data
public class TagVO {

    /** 标签 ID */
    private Long id;

    /** 标签名称 */
    private String name;

    /** 标签展示颜色 */
    private String color;

    /** 文章数（口径：仅统计已发布文章，实时查询得出） */
    private Integer articleCount;

    /** 真实被引用数（含草稿）——给删除按钮用 */
    private Long referenceCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最后更新时间 */
    private LocalDateTime updateTime;
}