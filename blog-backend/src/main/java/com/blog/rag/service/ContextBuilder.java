package com.blog.rag.service;

import com.blog.rag.chunk.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 上下文构建器：把检索到的块拼成喂给大模型的「资料」段落。
 *
 * <p>负责三件事：</p>
 * <ol>
 *   <li><b>去重</b>：内容高度重叠的块（相邻块因重叠窗口而部分重复）只保留得分高的</li>
 *   <li><b>截断</b>：按字符预算裁剪，控制 token 成本</li>
 *   <li><b>编号</b>：按顺序编号，出处列表与编号一一对应</li>
 * </ol>
 */
@Component
public class ContextBuilder {

    /**
     * 构建上下文。
     *
     * @param chunks        检索到的块（已按得分降序）
     * @param maxChars      上下文最大字符数
     * @return 上下文文本与对应的出处列表
     */
    public BuiltContext build(List<RetrievedChunk> chunks, int maxChars) {
        List<RetrievedChunk> deduped = deduplicate(chunks);

        StringBuilder sb = new StringBuilder();
        List<Reference> references = new ArrayList<>();
        int index = 1;

        for (RetrievedChunk chunk : deduped) {
            String body = chunk.getContent() == null ? "" : chunk.getContent().trim();
            if (body.isEmpty()) {
                continue;
            }
            String title = chunk.getArticleTitle() == null ? "" : chunk.getArticleTitle();
            String heading = chunk.getHeadingPath() == null ? "" : chunk.getHeadingPath();
            String header = "[" + index + "] 《" + title + "》" + (heading.isEmpty() ? "" : ">" + heading);

            // 预算检查：加上这一块会超就停（前面的块得分更高，优先保留）
            int needed = header.length() + body.length() + 2;
            if (sb.length() + needed > maxChars) {
                // 尝试截断这一块的最后一部分放进去
                int remain = maxChars - sb.length() - header.length() - 2;
                if (remain > 50) {
                    sb.append(header).append('\n')
                            .append(body, 0, remain).append("…\n\n");
                    references.add(buildReference(index, chunk, body.substring(0, remain) + "…"));
                    index++;
                }
                break;
            }

            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(header).append('\n').append(body).append('\n');
            references.add(buildReference(index, chunk, snippet(body)));
            index++;
        }

        return new BuiltContext(sb.toString().trim(), references);
    }

    /**
     * 去重：内容高度相似的块只保留一个。
     *
     * <p>相邻块之间有重叠窗口，相似度可能很高。用「前 100 字符的集合相似度」
     * 做粗判，避免引入复杂算法。</p>
     *
     * @param chunks 块列表（按得分降序）
     * @return 去重后的块列表
     */
    private List<RetrievedChunk> deduplicate(List<RetrievedChunk> chunks) {
        List<RetrievedChunk> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RetrievedChunk chunk : chunks) {
            String content = chunk.getContent() == null ? "" : chunk.getContent();
            String key = content.substring(0, Math.min(100, content.length())).trim();
            if (key.isEmpty() || seen.add(key)) {
                result.add(chunk);
            }
        }
        return result;
    }

    /**
     * 构造出处引用。
     *
     * @param index  编号
     * @param chunk  块
     * @param snippet 摘要片段
     * @return 出处
     */
    private Reference buildReference(int index, RetrievedChunk chunk, String snippet) {
        Reference ref = new Reference();
        ref.setIndex(index);
        ref.setArticleId(chunk.getArticleId());
        ref.setArticleTitle(chunk.getArticleTitle());
        ref.setHeadingPath(chunk.getHeadingPath());
        ref.setSnippet(snippet);
        ref.setScore(chunk.getScore());
        return ref;
    }

    /**
     * 截取摘要片段。
     *
     * @param content 块内容
     * @return 不超过 120 字的摘要
     */
    private String snippet(String content) {
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "…";
    }

    /**
     * 构建结果。
     */
    public static class BuiltContext {
        /** 拼好的上下文文本 */
        private final String context;
        /** 出处列表，顺序与上下文中的编号一致 */
        private final List<Reference> references;

        /**
         * @param context    上下文文本
         * @param references 出处列表
         */
        public BuiltContext(String context, List<Reference> references) {
            this.context = context;
            this.references = references;
        }

        /**
         * 获取上下文文本。
         *
         * @return 上下文文本
         */
        public String getContext() {
            return context;
        }

        /**
         * 获取出处列表。
         *
         * @return 出处列表
         */
        public List<Reference> getReferences() {
            return references;
        }
    }

    /**
     * 出处引用。
     */
    public static class Reference {
        /** 引用编号，与答案中的 [n] 对应 */
        private Integer index;
        /** 文章 ID */
        private Long articleId;
        /** 文章标题 */
        private String articleTitle;
        /** 标题路径 */
        private String headingPath;
        /** 内容摘要 */
        private String snippet;
        /** 检索得分 */
        private Double score;

        /**
         * 获取引用编号。
         *
         * @return 编号
         */
        public Integer getIndex() {
            return index;
        }

        /**
         * 设置引用编号。
         *
         * @param index 编号
         */
        public void setIndex(Integer index) {
            this.index = index;
        }

        /**
         * 获取文章 ID。
         *
         * @return 文章 ID
         */
        public Long getArticleId() {
            return articleId;
        }

        /**
         * 设置文章 ID。
         *
         * @param articleId 文章 ID
         */
        public void setArticleId(Long articleId) {
            this.articleId = articleId;
        }

        /**
         * 获取文章标题。
         *
         * @return 标题
         */
        public String getArticleTitle() {
            return articleTitle;
        }

        /**
         * 设置文章标题。
         *
         * @param articleTitle 标题
         */
        public void setArticleTitle(String articleTitle) {
            this.articleTitle = articleTitle;
        }

        /**
         * 获取标题路径。
         *
         * @return 标题路径
         */
        public String getHeadingPath() {
            return headingPath;
        }

        /**
         * 设置标题路径。
         *
         * @param headingPath 标题路径
         */
        public void setHeadingPath(String headingPath) {
            this.headingPath = headingPath;
        }

        /**
         * 获取摘要。
         *
         * @return 摘要
         */
        public String getSnippet() {
            return snippet;
        }

        /**
         * 设置摘要。
         *
         * @param snippet 摘要
         */
        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }

        /**
         * 获取得分。
         *
         * @return 得分
         */
        public Double getScore() {
            return score;
        }

        /**
         * 设置得分。
         *
         * @param score 得分
         */
        public void setScore(Double score) {
            this.score = score;
        }
    }
}
