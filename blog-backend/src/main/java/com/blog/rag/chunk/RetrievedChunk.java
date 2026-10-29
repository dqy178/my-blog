package com.blog.rag.chunk;

import lombok.Data;

/**
 * 检索块（内存索引中的最小单位）。
 *
 * <p>与 {@code ArticleChunk} 实体对应，但额外带上检索计算所需的派生字段
 * （词频、文档长度），避免索引结构与持久化结构耦合。</p>
 *
 * <p>「持久化」与「检索」两份结构分开的原因：数据库里的块只需要存原文，
 * 而 BM25 打分需要词频与文档长度——这些可以在建索引时算好放在内存里，
 * 不必落库。反过来，{@code score} 是查询时才算出来的，天然属于运行时字段。</p>
 */
@Data
public class RetrievedChunk {

    /** 所属文章 ID */
    private Long articleId;

    /** 文章标题（用于出处展示） */
    private String articleTitle;

    /** 块序号 */
    private Integer chunkIndex;

    /** 标题路径，如「基础 > 鉴权」。回答里标注出处时用它说明层级 */
    private String headingPath;

    /** 块内容 */
    private String content;

    /** 块字符数 */
    private Integer charCount;

    /**
     * 检索得分。BM25 得分越大越相关。
     *
     * <p>在 {@code retrieveByTerms} 中统一赋值；未参与检索的块保持默认值 0。</p>
     */
    private Double score = 0.0;

    /**
     * 词频表：词 → 在本块中出现的次数。
     *
     * <p>在构建索引时算好，检索时直接取用，避免每次查询都重新分词。
     * 这是 BM25 能做到「查询只做打分、不做分词」的前提。</p>
     */
    private java.util.Map<String, Integer> termFreq;

    /**
     * 本块的词数（用于 BM25 长度归一化）。
     *
     * <p>BM25 用 {@code |D| / avgdl} 惩罚过长文档，防止长块仅因词多就得分更高。</p>
     */
    private int termCount;
}
