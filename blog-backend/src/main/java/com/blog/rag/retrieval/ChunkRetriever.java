package com.blog.rag.retrieval;

import com.blog.rag.chunk.RetrievedChunk;

import java.util.Collection;
import java.util.List;

/**
 * 检索器抽象。
 *
 * <p>把「怎么找」与「找到之后怎么用」解耦：上层（{@code RagAskService}）只依赖本接口，
 * 因此第一阶段用 BM25、第二阶段换成向量检索时，上层（Prompt 组装、上下文构建、
 * 接口与前端）完全不需要改动。</p>
 */
public interface ChunkRetriever {

    /**
     * 按查询词检索相关块。
     *
     * @param query 原始查询（用户提问）
     * @param topK  返回块数量上限
     * @return 按得分降序排列的块列表；无命中时返回空列表
     */
    List<RetrievedChunk> retrieve(String query, int topK);

    /**
     * 用已处理好的词元检索。
     *
     * <p>与 {@link #retrieve(String, int)} 的区别：调用方（如 {@code QueryPreprocessor}）
     * 已经完成了分词与停用词、疑问词过滤，这里直接用词元检索，
     * <b>避免二次分词把词元又拆成单字+bigram</b>——那会引入检索噪声。</p>
     *
     * @param terms 检索词元
     * @param topK  返回块数量上限
     * @return 按得分降序排列的块列表；无命中时返回空列表
     */
    default List<RetrievedChunk> retrieveByTerms(Collection<String> terms, int topK) {
        return retrieve(String.join(" ", terms), topK);
    }

    /**
     * 重建索引（从数据源全量加载）。
     *
     * <p>检索器内部若维护了内存索引，实现类需在此重新加载；
     * 无状态实现可留空。</p>
     */
    default void rebuild() {
    }
}
