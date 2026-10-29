package com.blog.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时加载检索索引。
 *
 * <p>内存 BM25 索引是运行时投影，应用重启后需要从 {@code article_chunk} 表重新加载。
 * 这里只做「加载」不做「重新切块」——块已经在表里了，重新切一遍纯属浪费。</p>
 *
 * <p>如果表是空的（例如功能刚上线、存量文章还没建索引），
 * 日志会提示需要调用「全量重建索引」接口。</p>
 *
 * <p>加载失败<b>不阻止应用启动</b>：问答不可用但博客本身仍要能访问，
 * 否则一个可选功能会拖垮整个站点。</p>
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class RagIndexBootstrap implements ApplicationRunner {

    /** 索引服务 */
    private final IndexService indexService;

    /**
     * 应用启动完成后加载索引。
     *
     * @param args 启动参数（本实现不使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            int count = indexService.loadFromDatabase();
            if (count == 0) {
                log.warn("[RAG] 检索索引为空：请在后台调用「全量重建索引」接口（POST /api/admin/rag/reindex）"
                        + "为存量文章生成检索块");
            }
        } catch (Exception e) {
            // 索引加载失败不应阻止应用启动——问答功能不可用但博客本身仍可访问
            log.error("[RAG] 加载检索索引失败：{}", e.getMessage(), e);
        }
    }
}
