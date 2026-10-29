package com.blog.rag.event;

/**
 * 文章内容变更事件。
 *
 * <p>用于触发检索索引的重建。刻意用「事件 + 事务提交后监听」而不是直接在
 * Service 里调索引方法，原因见 {@code ArticleChunkIndexListener}。</p>
 */
public class ArticleChangedEvent {

    /** 文章 ID */
    private final Long articleId;

    /**
     * @param articleId 文章 ID
     */
    public ArticleChangedEvent(Long articleId) {
        this.articleId = articleId;
    }

    /**
     * 获取文章 ID。
     *
     * @return 文章 ID
     */
    public Long getArticleId() {
        return articleId;
    }
}
