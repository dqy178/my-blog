package com.blog.vo;

import lombok.Data;

/**
 * 回答出处。
 */
@Data
public class RagReferenceVO {

    /** 引用编号，与回答正文中的 [n] 对应 */
    private Integer index;

    /** 文章 ID，前端据此跳转文章详情 */
    private Long articleId;

    /** 文章标题 */
    private String articleTitle;

    /** 标题路径，如「基础 > 鉴权」 */
    private String headingPath;

    /** 命中的内容摘要 */
    private String snippet;

    /** 检索得分，用于调试与排序 */
    private Double score;
}
