package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章实体，对应数据库表 {@code article}。
 *
 * <p>字段上的注解含义：</p>
 * <ul>
 *   <li>{@code @TableLogic}：逻辑删除，删除操作转为把 {@code deleted} 置 1，
 *       查询自动追加 {@code deleted = 0}</li>
 *   <li>{@code @TableField(exist = false)}：非数据库字段，仅用于承载关联数据</li>
 *   <li>{@code updateStrategy = ALWAYS}：见 {@link #publishTime} 的说明</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("article")
public class Article implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 标题，长度上限 100 字符 */
    @TableField("title")
    private String title;

    /** 摘要，长度上限 300 字符 */
    @TableField("summary")
    private String summary;

    /** 正文，Markdown 原文（不在此层做渲染） */
    @TableField("content")
    private String content;

    /** 封面图 URL */
    @TableField("cover")
    private String cover;

    /** 所属分类 ID */
    @TableField("category_id")
    private Long categoryId;

    /** 状态：0=草稿，1=已发布，2=回收站 */
    @TableField("status")
    private Integer status;

    /** 是否置顶：1=是，0=否。列表排序时置顶优先 */
    @TableField("is_top")
    private Integer isTop;

    /** 浏览量。直接存库以便 SQL 层排序与聚合 */
    @TableField("view_count")
    private Long viewCount;

    /** 已通过的评论数（含回复），评论审核状态变化时重算 */
    @TableField("comment_count")
    private Long commentCount;

    /** 是否允许评论：1=允许，0=禁止 */
    @TableField("allow_comment")
    private Integer allowComment;

    /** 创建时间。草稿排序与「开始写作时间」提示使用 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    // ALWAYS: 下架时需要把 publish_time 显式置为 NULL，
    // MyBatis-Plus 默认的 NOT_NULL 策略会跳过 null 字段导致无法清空
    @TableField(value = "publish_time", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime publishTime;

    /** 逻辑删除标记：0=未删除，1=已删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 分类名称（非数据库字段，装配 VO 时填充） */
    @TableField(exist = false)
    private String categoryName;

    /** 标签名称列表（非数据库字段，装配 VO 时填充） */
    @TableField(exist = false)
    private java.util.List<String> tagNames;
}