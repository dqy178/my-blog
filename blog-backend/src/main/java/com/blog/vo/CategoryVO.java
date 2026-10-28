package com.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分类出参对象。
 */
@Data
public class CategoryVO {

    /** 分类 ID */
    private Long id;

    /** 父分类 ID。null 表示顶级分类 */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 分类描述 */
    private String description;

    /** 分类图标标识 */
    private String icon;

    /** 排序值 */
    private Integer sort;

    /** 文章数（口径：仅统计已发布文章，实时查询得出） */
    private Integer articleCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最后更新时间 */
    private LocalDateTime updateTime;

    /** 子分类列表，装配分类树时填充 */
    private List<CategoryVO> children;

    /** 真实被引用数（含草稿）——给删除按钮用 */
    private Long referenceCount;
}