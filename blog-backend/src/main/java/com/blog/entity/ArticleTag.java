package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文章与标签的关联实体，对应数据库表 {@code article_tag}。
 *
 * <p>唯一索引 {@code uk_article_tag(article_id, tag_id)} 保证同一篇文章不会重复关联同一标签。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("article_tag")
public class ArticleTag implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 文章 ID */
    @TableField("article_id")
    private Long articleId;

    /** 标签 ID */
    @TableField("tag_id")
    private Long tagId;
}