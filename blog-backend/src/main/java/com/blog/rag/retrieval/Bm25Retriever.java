package com.blog.rag.retrieval;

import com.blog.rag.chunk.ChineseTokenizer;
import com.blog.rag.chunk.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 BM25 的内存关键词检索器。
 *
 * <p>选择内存 BM25 而非数据库全文索引的理由：</p>
 * <ul>
 *   <li><b>打分可控</b>：BM25 有明确的饱和因子 k1 与长度惩罚 b，可与 TF-IDF 做量化对比</li>
 *   <li><b>不依赖特定数据库</b>：MySQL 的 ngram 全文索引相关度打分粗糙，
 *       且中文短查询容易泛匹配</li>
 *   <li><b>可平滑替换</b>：第二阶段换成向量检索时，本类整体被 {@code VectorRetriever} 取代，
 *       上层无需改动</li>
 * </ul>
 *
 * <p>BM25 打分公式：</p>
 * <pre>
 * score(D, Q) = Σ IDF(q) · ( f(q,D) · (k1 + 1) ) / ( f(q,D) + k1 · (1 - b + b · |D| / avgdl) )
 *
 *   IDF(q) = ln( 1 + (N - n(q) + 0.5) / (n(q) + 0.5) )
 * </pre>
 * <ul>
 *   <li>{@code f(q,D)}：词 q 在文档 D 中的词频</li>
 *   <li>{@code |D| / avgdl}：文档长度与平均长度之比，惩罚过长文档</li>
 *   <li>{@code k1}（默认 1.5）：词频饱和因子。k1 越大，词频增益越接近线性；
 *       取 0 则退化为只看词是否出现</li>
 *   <li>{@code b}（默认 0.75）：长度惩罚强度。b=0 表示不惩罚长文档</li>
 * </ul>
 *
 * <p>相比 TF-IDF 的两点改进：<b>词频饱和</b>（同一个词出现 10 次不会比出现 2 次重要 5 倍）
 * 与<b>文档长度归一化</b>，因此不会系统性偏好短文档。</p>
 */
@Slf4j
@Component
public class Bm25Retriever implements ChunkRetriever {

    /** BM25 词频饱和因子 */
    private static final double K1 = 1.5;

    /** BM25 长度惩罚因子 */
    private static final double B = 0.75;

    private final ChineseTokenizer tokenizer = new ChineseTokenizer();

    /** 内存索引：块键（articleId:chunkIndex） → 块（含预计算词频） */
    private final Map<String, RetrievedChunk> chunks = new ConcurrentHashMap<>();

    /** 倒排索引：词 → 包含该词的块键集合 */
    private final Map<String, Set<String>> invertedIndex = new ConcurrentHashMap<>();

    /** 每个词出现在多少个块中（用于计算 IDF） */
    private final Map<String, Integer> docFreq = new ConcurrentHashMap<>();

    /** 块总数 */
    private volatile int totalChunks = 0;

    /** 所有块的平均词数（BM25 长度归一化用） */
    private volatile double avgTermCount = 0.0;

    /**
     * 写入或更新一个块到索引。
     *
     * <p>同一 {@code articleId:chunkIndex} 视为同一块，重复写入会先移除旧版本。</p>
     *
     * @param chunk 已预计算好词频的块
     */
    public void put(RetrievedChunk chunk) {
        String key = key(chunk.getArticleId(), chunk.getChunkIndex());
        removeInternal(key);

        chunks.put(key, chunk);
        for (String term : chunk.getTermFreq().keySet()) {
            invertedIndex.computeIfAbsent(term, k -> ConcurrentHashMap.newKeySet()).add(key);
            docFreq.merge(term, 1, Integer::sum);
        }
        recomputeStats();
    }

    /**
     * 删除某篇文章的全部块。
     *
     * @param articleId 文章 ID
     */
    public void removeByArticle(Long articleId) {
        String prefix = articleId + ":";
        List<String> toRemove = chunks.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .toList();
        toRemove.forEach(this::removeInternal);
        recomputeStats();
    }

    /**
     * 清空索引。
     */
    public void clear() {
        chunks.clear();
        invertedIndex.clear();
        docFreq.clear();
        totalChunks = 0;
        avgTermCount = 0.0;
    }

    /**
     * 当前索引中的块数。
     *
     * @return 块数
     */
    public int size() {
        return chunks.size();
    }

    @Override
    public void rebuild() {
        clear();
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || chunks.isEmpty() || topK <= 0) {
            return List.of();
        }
        return retrieveByTerms(tokenizer.tokenize(query), topK);
    }

    @Override
    public List<RetrievedChunk> retrieveByTerms(java.util.Collection<String> queryTerms, int topK) {
        if (queryTerms == null || queryTerms.isEmpty() || chunks.isEmpty() || topK <= 0) {
            return List.of();
        }

        Set<String> terms = new LinkedHashSet<>(queryTerms);
        if (terms.isEmpty()) {
            return List.of();
        }
        // 候选集：包含任一查询词的块
        Set<String> candidates = new HashSet<>();
        for (String term : queryTerms) {
            Set<String> keys = invertedIndex.get(term);
            if (keys != null) {
                candidates.addAll(keys);
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<RetrievedChunk> scored = new ArrayList<>(candidates.size());
        for (String key : candidates) {
            RetrievedChunk chunk = chunks.get(key);
            if (chunk == null) {
                continue;
            }
            double score = bm25Score(chunk, terms);
            if (score > 0) {
                chunk.setScore(score);
                scored.add(chunk);
            }
        }

        scored.sort(Comparator.comparingDouble(RetrievedChunk::getScore).reversed());
        return scored.size() > topK ? new ArrayList<>(scored.subList(0, topK)) : scored;
    }

    /**
     * 计算一个块对一组查询词的 BM25 得分。
     *
     * @param chunk      块
     * @param queryTerms 查询词集合
     * @return 得分
     */
    private double bm25Score(RetrievedChunk chunk, Set<String> queryTerms) {
        double score = 0.0;
        int docLen = chunk.getTermCount();
        for (String term : queryTerms) {
            int tf = chunk.getTermFreq().getOrDefault(term, 0);
            if (tf == 0) {
                continue;
            }
            double idf = idf(term);
            double denominator = tf + K1 * (1 - B + B * docLen / Math.max(avgTermCount, 1.0));
            score += idf * (tf * (K1 + 1)) / denominator;
        }
        return score;
    }

    /**
     * 计算词的逆文档频率。
     *
     * @param term 词
     * @return IDF 值
     */
    private double idf(String term) {
        int n = docFreq.getOrDefault(term, 0);
        int N = Math.max(totalChunks, 1);
        // 加 1 平滑，避免高频词 IDF 为 0（那样出现再多次也不贡献得分）
        return Math.log(1 + (N - n + 0.5) / (n + 0.5));
    }

    /**
     * 从索引中移除一个块。
     *
     * @param key 块键
     */
    private void removeInternal(String key) {
        RetrievedChunk removed = chunks.remove(key);
        if (removed == null) {
            return;
        }
        for (String term : removed.getTermFreq().keySet()) {
            Set<String> keys = invertedIndex.get(term);
            if (keys != null) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    invertedIndex.remove(term);
                    docFreq.remove(term);
                }
            }
        }
    }

    /**
     * 重新计算块总数与平均词数。
     */
    private void recomputeStats() {
        int count = 0;
        long totalTerms = 0;
        for (RetrievedChunk c : chunks.values()) {
            count++;
            totalTerms += c.getTermCount();
        }
        totalChunks = count;
        avgTermCount = count == 0 ? 0.0 : (double) totalTerms / count;
    }

    /**
     * 构造块键。
     *
     * @param articleId  文章 ID
     * @param chunkIndex 块序号
     * @return 块键
     */
    private String key(Long articleId, Integer chunkIndex) {
        return articleId + ":" + chunkIndex;
    }

    /**
     * 供调试：查看某个词的文档频率。
     *
     * @param term 词
     * @return 包含该词的块数
     */
    public int documentFrequency(String term) {
        return docFreq.getOrDefault(term, 0);
    }

    /**
     * 供调试：查看当前平均块长度（词数）。
     *
     * @return 平均词数
     */
    public double averageTermCount() {
        return avgTermCount;
    }
}
