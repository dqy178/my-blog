package com.blog.rag.service;

import com.blog.rag.event.ArticleChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文章变更事件监听器：触发检索索引重建。
 *
 * <p><b>为什么不用「在 Service 里直接调 @Async 方法」</b>：</p>
 * <ol>
 *   <li><b>@Async 自调用失效</b>：同类内部调用（如 {@code this.reindexAsync(id)}）
 *       不经过 Spring 代理，异步根本不生效，索引会同步阻塞保存接口</li>
 *   <li><b>事务回滚后索引已建</b>：文章保存是 {@code @Transactional} 的，
 *       若在事务提交前就建索引，事务回滚后索引里会残留未发布的草稿内容</li>
 * </ol>
 *
 * <p>因此这里用「事件 + 事务提交后监听 + 异步」三件套：
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 保证只在事务成功提交后触发，
 * {@code @Async} 保证不阻塞调用方。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleChunkIndexListener {

    private final IndexService indexService;

    /**
     * 处理文章内容变更事件。
     *
     * @param event 事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArticleChanged(ArticleChangedEvent event) {
        try {
            indexService.reindexArticle(event.getArticleId());
        } catch (Exception e) {
            // 索引失败不能影响文章本身的保存结果，
            // 管理员可通过「全量重建索引」接口修复。
            log.error("[RAG] 文章 {} 索引重建失败：{}", event.getArticleId(), e.getMessage(), e);
        }
    }
}
