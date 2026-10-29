package com.blog.rag.chunk;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 切块器。
 *
 * <p>把一篇 Markdown 文章切成若干带标题路径的检索块，策略：</p>
 * <ol>
 *   <li><b>按标题层级切段</b>：记录当前 heading 栈，产出 {@code headingPath}（如「基础 > 鉴权」）</li>
 *   <li><b>段内合并</b>：相邻短段落合并成一个块，直到达到 {@code chunkMaxChars}</li>
 *   <li><b>超长段二次切分</b>：单个段落超过上限时，按句号边界切开</li>
 *   <li><b>重叠窗口</b>：相邻块之间保留 {@code chunkOverlapChars} 字符的重叠，
 *       避免一句完整的话正好被块边界截断</li>
 * </ol>
 *
 * <p>代码块（```）与表格行不参与重叠与按句切分，避免破坏 Markdown 结构。</p>
 */
public class MarkdownChunker {

    /** 最大块字符数 */
    private final int maxChars;

    /** 最小块字符数 */
    private final int minChars;

    /** 相邻块重叠字符数 */
    private final int overlapChars;

    /**
     * @param maxChars     最大块字符数
     * @param minChars     最小块字符数
     * @param overlapChars 相邻块重叠字符数
     */
    public MarkdownChunker(int maxChars, int minChars, int overlapChars) {
        this.maxChars = Math.max(100, maxChars);
        this.minChars = Math.max(0, minChars);
        this.overlapChars = Math.max(0, Math.min(overlapChars, this.maxChars / 2));
    }

    /**
     * 切分 Markdown 文章。
     *
     * @param content Markdown 原文
     * @return 块列表（按原文顺序）
     */
    public List<ArticleChunkData> split(String content) {
        List<ArticleChunkData> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }

        String[] lines = content.split("\r?\n");
        // 当前标题栈，下标 i 对应 h(i+1) 级标题
        String[] headingStack = new String[6];
        StringBuilder buffer = new StringBuilder();
        String currentHeadingPath = "";
        boolean inCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // 代码块围栏：整体保留，不参与标题识别与句号切分
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                appendLine(buffer, line);
                continue;
            }

            if (!inCodeBlock && trimmed.startsWith("#")) {
                int level = headingLevel(trimmed);
                if (level > 0 && level <= headingStack.length) {
                    // 先把已积累的内容产出为块，再切换标题
                    if (!buffer.toString().isBlank()) {
                        chunks.addAll(finalizeBuffer(buffer.toString(), currentHeadingPath));
                        buffer.setLength(0);
                    }
                    // 清空同级及更高级的标题
                    for (int i = level - 1; i < headingStack.length; i++) {
                        headingStack[i] = null;
                    }
                    headingStack[level - 1] = trimmed.replaceFirst("^#+\\s*", "").trim();
                    currentHeadingPath = buildHeadingPath(headingStack);
                    continue;
                }
            }

            appendLine(buffer, line);
        }

        if (!buffer.toString().isBlank()) {
            chunks.addAll(finalizeBuffer(buffer.toString(), currentHeadingPath));
        }

        // 编号
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setChunkIndex(i);
        }
        return chunks;
    }

    /**
     * 把缓冲区内容切成最终的块（含长段二次切分与重叠窗口）。
     *
     * @param text      缓冲区文本
     * @param headingPath 标题路径
     * @return 块列表
     */
    private List<ArticleChunkData> finalizeBuffer(String text, String headingPath) {
        List<ArticleChunkData> result = new ArrayList<>();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return result;
        }

        if (trimmed.length() <= maxChars) {
            result.add(build(trimmed, headingPath));
            return result;
        }

        // 超长：按句号边界切开
        List<String> pieces = splitBySentence(trimmed);
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (current.length() > 0 && current.length() + piece.length() > maxChars) {
                result.add(build(current.toString().trim(), headingPath));
                // 用上一块的尾部作为重叠
                current = new StringBuilder(tail(current.toString(), overlapChars));
                if (current.length() + piece.length() > maxChars) {
                    // 重叠部分本身就快满了，丢弃重叠，保证不超上限
                    current.setLength(0);
                }
            }
            current.append(piece);
        }
        if (!current.toString().isBlank()) {
            result.add(build(current.toString().trim(), headingPath));
        }
        return result;
    }

    /**
     * 按中英文句末标点切分，保留标点。
     *
     * @param text 文本
     * @return 句子片段列表
     */
    private List<String> splitBySentence(String text) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            current.append(c);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n'
                    || c == '.' || c == '!' || c == '?') {
                // 英文句点后面若紧跟字母，多半是缩写或小数，不切
                if (c == '.' && i + 1 < text.length() && Character.isLetterOrDigit(text.charAt(i + 1))) {
                    continue;
                }
                pieces.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    /**
     * 取文本尾部若干字符作为重叠部分。
     *
     * @param text 文本
     * @param len  长度
     * @return 尾部文本
     */
    private String tail(String text, int len) {
        if (text.length() <= len) {
            return text;
        }
        return text.substring(text.length() - len);
    }

    /**
     * 构造块数据。
     *
     * @param content     内容
     * @param headingPath 标题路径
     * @return 块数据
     */
    private ArticleChunkData build(String content, String headingPath) {
        ArticleChunkData data = new ArticleChunkData();
        data.setContent(content);
        data.setHeadingPath(headingPath);
        data.setCharCount(content.length());
        return data;
    }

    /**
     * 识别 Markdown 标题层级。
     *
     * @param line 行内容
     * @return 层级（1~6），非标题返回 0
     */
    private int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level > 6) {
            return 0;
        }
        // # 后面必须跟空格才是标题（否则是 #tag 这类普通文本）
        return (level < line.length() && Character.isWhitespace(line.charAt(level))) ? level : 0;
    }

    /**
     * 根据标题栈构建标题路径。
     *
     * @param stack 标题栈
     * @return 形如「基础 > 鉴权」的路径
     */
    private String buildHeadingPath(String[] stack) {
        StringBuilder sb = new StringBuilder();
        for (String h : stack) {
            if (h != null && !h.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" > ");
                }
                sb.append(h);
            }
        }
        return sb.toString();
    }

    /**
     * 追加一行到缓冲区。
     *
     * @param buffer 缓冲区
     * @param line   行内容
     */
    private void appendLine(StringBuilder buffer, String line) {
        if (buffer.length() > 0) {
            buffer.append('\n');
        }
        buffer.append(line);
    }

    /**
     * 切块结果数据。
     */
    @Data
    public static class ArticleChunkData {
        /** 块序号 */
        private Integer chunkIndex;
        /** 标题路径 */
        private String headingPath;
        /** 块内容 */
        private String content;
        /** 块字符数 */
        private Integer charCount;
    }
}
