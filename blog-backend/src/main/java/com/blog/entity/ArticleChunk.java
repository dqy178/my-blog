package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章检索块（RAG 知识库单元）。
 *
 * <p>一篇 Markdown 文章被 {@code MarkdownChunker} 切分成若干块后落库，
 * 检索时以块为最小单位召回，再拼成上下文喂给大模型。</p>
 *
 * <p>按「块」而非「文章」存储的原因：整篇文章往往超过上下文预算，
 * 且一篇长文里通常只有一小部分与具体问题相关。</p>
 */
@Data
@TableName("article_chunk")
public class ArticleChunk implements Serializable {

    /** 主键 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属文章 ID */
    private Long articleId;

    /** 块序号，同一文章内从 0 递增 */
    private Integer chunkIndex;

    /** 标题路径，如「基础 > 鉴权 > JWT」。用于回答时标注出处层级 */
    private String headingPath;

    /** 块内容（Markdown 片段） */
    private String content;

    /** 块字符数 */
    private Integer charCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
