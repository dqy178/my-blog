package com.blog.rag.service;

import com.blog.config.RagProperties;
import com.blog.entity.Article;
import com.blog.entity.ArticleChunk;
import com.blog.mapper.ArticleChunkMapper;
import com.blog.mapper.ArticleMapper;
import com.blog.rag.chunk.ChineseTokenizer;
import com.blog.rag.chunk.MarkdownChunker;
import com.blog.rag.chunk.RetrievedChunk;
import com.blog.rag.retrieval.Bm25Retriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索索引服务。
 *
 * <p>负责把已发布文章切块落库，并同步到内存 BM25 索引。</p>
 *
 * <p><b>索引与数据库的关系</b>：{@code article_chunk} 表是持久化的真相来源，
 * 内存索引是它的运行时投影。任何时候都可以用 {@link #rebuildAll()} 从表重建，
 * 因此内存索引损坏不会造成数据丢失。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {

    private final ArticleChunkMapper articleChunkMapper;
    private final ArticleMapper articleMapper;
    private final Bm25Retriever retriever;
    private final RagProperties properties;

    private final ChineseTokenizer tokenizer = new ChineseTokenizer();

    /**
     * 重建单篇文章的索引。
     *
     * <p>先删旧块再切新块，保证块序号连续、不残留被删掉的段落。</p>
     *
     * <p>草稿与回收站文章不建索引 —— 检索结果不应包含未发布内容。</p>
     *
     * @param articleId 文章 ID
     * @return 生成的块数
     */
    @Transactional
    public int reindexArticle(Long articleId) {
        if (articleId == null) {
            return 0;
        }
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            // 文章已被删除：清掉残留块与内存索引
            articleChunkMapper.deleteByArticleId(articleId);
            retriever.removeByArticle(articleId);
            return 0;
        }
        if (!Integer.valueOf(1).equals(article.getStatus())) {
            // 非发布状态：撤掉索引
            articleChunkMapper.deleteByArticleId(articleId);
            retriever.removeByArticle(articleId);
            log.info("[IndexService] 文章 {} 状态非发布，撤回索引", articleId);
            return 0;
        }

        articleChunkMapper.deleteByArticleId(articleId);
        List<ArticleChunk> saved = buildChunks(article);
        if (!saved.isEmpty()) {
            articleChunkMapper.insertBatch(saved);
        }
        retriever.removeByArticle(articleId);
        for (ArticleChunk chunk : saved) {
            retriever.put(toRetrievedChunk(chunk, article.getTitle()));
        }
        log.info("[IndexService] 文章「{}」重建索引完成，{} 块", article.getTitle(), saved.size());
        return saved.size();
    }

    /**
     * 全量重建索引。
     *
     * <p>用于首次部署（存量文章没有块）或索引损坏后的修复。</p>
     *
     * @return 处理的文章数
     */
    @Transactional
    public int rebuildAll() {
        List<Article> articles = articleMapper.selectList(null);
        articleChunkMapper.deleteAll();
        retriever.clear();

        int count = 0;
        for (Article article : articles) {
            if (!Integer.valueOf(1).equals(article.getStatus())) {
                continue;
            }
            List<ArticleChunk> chunks = buildChunks(article);
            if (!chunks.isEmpty()) {
                articleChunkMapper.insertBatch(chunks);
                for (ArticleChunk chunk : chunks) {
                    retriever.put(toRetrievedChunk(chunk, article.getTitle()));
                }
            }
            count++;
        }
        log.info("[IndexService] 全量索引重建完成：{} 篇文章，{} 个块", count, retriever.size());
        return count;
    }

    /**
     * 从内存索引加载全部块（不落库，仅刷新运行时投影）。
     *
     * <p>应用启动时调用：此时表里已有块（例如上一轮运行留下的），
     * 直接加载即可，无需重新切块。</p>
     *
     * @return 加载的块数
     */
    public int loadFromDatabase() {
        retriever.clear();
        List<ArticleChunk> all = articleChunkMapper.selectAll();
        Map<Long, String> titles = new HashMap<>();
        for (ArticleChunk chunk : all) {
            String title = titles.computeIfAbsent(chunk.getArticleId(), id -> {
                Article a = articleMapper.selectById(id);
                return a == null ? "" : a.getTitle();
            });
            retriever.put(toRetrievedChunk(chunk, title));
        }
        log.info("[IndexService] 已从数据库加载 {} 个块到内存索引", all.size());
        return all.size();
    }

    /**
     * 把文章切块并转成待入库的实体。
     *
     * @param article 文章
     * @return 块实体列表
     */
    private List<ArticleChunk> buildChunks(Article article) {
        MarkdownChunker chunker = new MarkdownChunker(
                properties.getChunkMaxChars(),
                properties.getChunkMinChars(),
                properties.getChunkOverlapChars());
        List<MarkdownChunker.ArticleChunkData> dataList = chunker.split(article.getContent());

        List<ArticleChunk> result = new ArrayList<>(dataList.size());
        for (MarkdownChunker.ArticleChunkData data : dataList) {
            ArticleChunk chunk = new ArticleChunk();
            chunk.setArticleId(article.getId());
            chunk.setChunkIndex(data.getChunkIndex());
            chunk.setHeadingPath(data.getHeadingPath());
            chunk.setContent(data.getContent());
            chunk.setCharCount(data.getCharCount());
            chunk.setCreateTime(LocalDateTime.now());
            result.add(chunk);
        }
        return result;
    }

    /**
     * 把持久化实体转成内存索引用的块（含词频计算）。
     *
     * @param chunk        块实体
     * @param articleTitle 文章标题
     * @return 内存索引块
     */
    private RetrievedChunk toRetrievedChunk(ArticleChunk chunk, String articleTitle) {
        RetrievedChunk rc = new RetrievedChunk();
        rc.setArticleId(chunk.getArticleId());
        rc.setArticleTitle(articleTitle);
        rc.setChunkIndex(chunk.getChunkIndex());
        rc.setHeadingPath(chunk.getHeadingPath());
        rc.setContent(chunk.getContent());
        rc.setCharCount(chunk.getCharCount());

        // 词频统计：分词后按词计数
        Map<String, Integer> freq = new HashMap<>();
        List<String> terms = tokenizer.tokenize(chunk.getContent());
        for (String term : terms) {
            freq.merge(term, 1, Integer::sum);
        }
        rc.setTermFreq(freq);
        rc.setTermCount(terms.size());
        return rc;
    }
}
