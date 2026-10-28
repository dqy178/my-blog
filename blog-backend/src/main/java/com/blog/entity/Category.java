package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分类实体，对应数据库表 {@code category}。
 *
 * <p>分类支持多级：{@code parentId} 为 NULL 表示顶级分类，否则指向父分类。
 * 树形结构由 {@code CategoryServiceImpl.getCategoryTree()} 在内存中装配，
 * 不在数据库中做递归查询。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("category")
public class Category implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 分类名称，唯一 */
    @TableField("name")
    private String name;

    /** 分类描述 */
    @TableField("description")
    private String description;

    /** 分类图标标识 */
    @TableField("icon")
    private String icon;

    /** 排序值，越小越靠前 */
    @TableField("sort")
    private Integer sort;

    /** 父分类 ID。NULL 表示顶级分类 */
    @TableField("parent_id")
    private Long parentId;

    /** 文章数（口径：仅统计已发布文章） */
    @TableField("article_count")
    private Integer articleCount;

    /** 子分类列表（非数据库字段，装配分类树时填充） */
    @TableField(exist = false)
    private java.util.List<Category> children;

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