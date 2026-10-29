package com.blog.rag.retrieval;

import com.blog.rag.chunk.ChineseTokenizer;
import com.blog.rag.chunk.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BM25 检索器单元测试。
 *
 * <p>重点验证三件事：相关性排序正确（相关的排前面）、
 * BM25 相对 TF-IDF 的长度归一化生效（不会系统性偏好短文档）、
 * 以及空查询与未命中场景。</p>
 */
class Bm25RetrieverTest {

    private final Bm25Retriever retriever = new Bm25Retriever();
    private final ChineseTokenizer tokenizer = new ChineseTokenizer();

    @BeforeEach
    void setUp() {
        retriever.clear();
    }

    /**
     * 构造一个已计算好词频的块。
     *
     * @param id      文章 ID
     * @param index   块序号
     * @param title   标题
     * @param content 内容
     * @return 块
     */
    private RetrievedChunk chunk(Long id, int index, String title, String content) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setArticleId(id);
        chunk.setChunkIndex(index);
        chunk.setArticleTitle(title);
        chunk.setHeadingPath(title);
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        Map<String, Integer> freq = new HashMap<>();
        List<String> terms = tokenizer.tokenize(content);
        for (String t : terms) {
            freq.merge(t, 1, Integer::sum);
        }
        chunk.setTermFreq(freq);
        chunk.setTermCount(terms.size());
        return chunk;
    }

    @Test
    @DisplayName("相关的块应排在前面")
    void retrieve_shouldRankRelevantChunkFirst() {
        retriever.put(chunk(1L, 0, "Redis 缓存", "Redis 的缓存穿透是指请求一个不存在的数据。"));
        retriever.put(chunk(2L, 0, "MySQL 索引", "MySQL 的索引可以提升查询性能。"));
        retriever.put(chunk(3L, 0, "Docker 镜像", "Docker 镜像构建可以使用多阶段构建。"));

        List<RetrievedChunk> result = retriever.retrieve("缓存穿透怎么解决", 3);

        assertFalse(result.isEmpty(), "应召回相关块");
        assertEquals(1L, result.get(0).getArticleId(), "最相关的应是 Redis 缓存那篇");
    }

    @Test
    @DisplayName("完全无关的查询不应召回")
    void retrieve_unrelatedQuery_shouldReturnEmpty() {
        retriever.put(chunk(1L, 0, "Redis 缓存", "Redis 的缓存穿透是指请求不存在的数据。"));

        List<RetrievedChunk> result = retriever.retrieve("今天天气怎么样", 5);

        assertTrue(result.isEmpty(), "无关查询不应召回任何块，实际: " + result.size());
    }

    @Test
    @DisplayName("空查询应返回空结果")
    void retrieve_emptyQuery_shouldReturnEmpty() {
        retriever.put(chunk(1L, 0, "Redis", "Redis 缓存"));

        assertTrue(retriever.retrieve("", 5).isEmpty());
        assertTrue(retriever.retrieve(null, 5).isEmpty());
        assertTrue(retriever.retrieve("   ", 5).isEmpty());
    }

    @Test
    @DisplayName("topK 应限制返回数量")
    void retrieve_shouldRespectTopK() {
        for (int i = 0; i < 10; i++) {
            retriever.put(chunk((long) i, 0, "文章" + i, "缓存穿透 缓存穿透 redis 缓存"));
        }

        List<RetrievedChunk> result = retriever.retrieve("缓存穿透", 3);

        assertEquals(3, result.size(), "返回数量应不超过 topK");
    }

    @Test
    @DisplayName("删除文章后其块应不再被召回")
    void removeByArticle_shouldRemoveItsChunks() {
        retriever.put(chunk(1L, 0, "Redis", "Redis 缓存穿透"));
        retriever.put(chunk(2L, 0, "MySQL", "MySQL 索引优化"));

        retriever.removeByArticle(1L);

        List<RetrievedChunk> result = retriever.retrieve("缓存穿透", 5);
        assertTrue(result.isEmpty(), "已删除文章的块不应再被召回");
        assertEquals(1, retriever.size(), "索引中应只剩 1 个块");
    }

    @Test
    @DisplayName("重复写入同一块应覆盖而非累加")
    void put_sameKey_shouldReplace() {
        retriever.put(chunk(1L, 0, "Redis", "缓存穿透"));
        retriever.put(chunk(1L, 0, "Redis", "缓存穿透"));

        assertEquals(1, retriever.size(), "同一 articleId:chunkIndex 应只保留一份");
    }

    @Test
    @DisplayName("BM25 长度归一化应避免系统性偏好短文档")
    void retrieve_shouldNotSystematicallyFavorShortDocs() {
        // 短文档：关键词出现 1 次
        retriever.put(chunk(1L, 0, "短", "缓存"));
        // 长文档：关键词出现很多次，但文档整体很长
        StringBuilder sb = new StringBuilder("缓存 ");
        for (int i = 0; i < 200; i++) {
            sb.append("无关内容填充 ");
        }
        retriever.put(chunk(2L, 0, "长", sb.toString()));

        List<RetrievedChunk> result = retriever.retrieve("缓存", 5);

        assertFalse(result.isEmpty());
        // 纯 TF-IDF 下长文档会因为词频高而得分极高；
        // BM25 的词频饱和 + 长度归一化应让两者的差距明显收窄。
        double ratio = result.get(0).getScore() / Math.max(result.get(1).getScore(), 0.0001);
        assertTrue(ratio < 5.0,
                "BM25 的饱和与长度归一化应抑制长文档的词频优势，实际得分比: " + ratio);
    }

    @Test
    @DisplayName("空索引时检索应返回空结果")
    void retrieve_emptyIndex_shouldReturnEmpty() {
        assertTrue(retriever.retrieve("缓存", 5).isEmpty());
    }
}
