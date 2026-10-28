package com.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章出参对象。
 *
 * <p>相比实体多了 {@code categoryName} 与 {@code tags} 等装配字段，
 * 由 Service 层批量查询后填充，避免前端二次请求。</p>
 */
@Data
public class ArticleVO {

    /** 文章 ID */
    private Long id;

    /** 标题 */
    private String title;

    /** 摘要 */
    private String summary;

    /** 正文（Markdown 原文） */
    private String content;

    /** 封面图 URL */
    private String cover;

    /** 所属分类 ID */
    private Long categoryId;

    /** 所属分类名称（装配字段） */
    private String categoryName;

    /** 状态：0=草稿，1=发布，2=回收站 */
    private Integer status;

    /** 是否置顶：1=是 */
    private Integer isTop;

    /** 浏览量 */
    private Long viewCount;

    /** 已通过的评论数（含回复） */
    private Long commentCount;

    /** 是否允许评论：1=允许 */
    private Integer allowComment;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最后更新时间 */
    private LocalDateTime updateTime;

    /** 发布时间。下架后为 null，前台显示「无」 */
    private LocalDateTime publishTime;

    /** 标签名称列表（装配字段） */
    private List<String> tagNames;

    /** 标签完整信息列表（装配字段） */
    private List<TagVO> tags;
}