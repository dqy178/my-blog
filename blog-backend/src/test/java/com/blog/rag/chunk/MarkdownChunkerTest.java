package com.blog.rag.chunk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Markdown 切块器单元测试。
 *
 * <p>覆盖：标题路径提取、超长文本二次切分、空输入处理、
 * 以及「代码块内的 # 不应被当作标题」这一容易出错的边界。</p>
 */
class MarkdownChunkerTest {

    private final MarkdownChunker chunker = new MarkdownChunker(500, 80, 100);

    @Test
    @DisplayName("应提取标题路径")
    void split_shouldExtractHeadingPath() {
        String md = """
                # 一级标题

                这是引言段落。

                ## 二级标题

                这是二级标题下的内容。

                ### 三级标题

                三级标题下的内容。
                """;

        List<MarkdownChunker.ArticleChunkData> chunks = chunker.split(md);

        assertFalse(chunks.isEmpty(), "应至少切出一个块");
        assertTrue(chunks.get(0).getHeadingPath().contains("一级标题"),
                "首个块的标题路径应包含「一级标题」，实际: " + chunks.get(0).getHeadingPath());
        assertTrue(chunks.get(chunks.size() - 1).getHeadingPath().contains("三级标题"),
                "末个块的标题路径应包含「三级标题」");
    }

    @Test
    @DisplayName("块序号应连续递增")
    void split_shouldAssignContinuousIndex() {
        String md = "# 标题\n\n段落一。\n\n## 小节\n\n段落二。\n\n段落三。";

        List<MarkdownChunker.ArticleChunkData> chunks = chunker.split(md);

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getChunkIndex(), "块序号应从 0 连续递增");
        }
    }

    @Test
    @DisplayName("超长文本应被切分成多个块且不超过上限")
    void split_longText_shouldSplitWithinLimit() {
        StringBuilder sb = new StringBuilder("# 长文\n\n");
        for (int i = 0; i < 60; i++) {
            sb.append("这是第").append(i).append("个句子，用于把文章撑到超过单块上限的长度。");
        }
        String md = sb.toString();

        List<MarkdownChunker.ArticleChunkData> chunks = chunker.split(md);

        assertTrue(chunks.size() > 1, "超长文本应切出多个块，实际: " + chunks.size());
        for (MarkdownChunker.ArticleChunkData chunk : chunks) {
            assertTrue(chunk.getCharCount() <= 500,
                    "单块字符数不应超过上限，实际: " + chunk.getCharCount());
        }
    }

    @Test
    @DisplayName("代码块内的 # 不应被识别为标题")
    void split_shouldNotTreatCodeHashAsHeading() {
        String md = """
                # 真实标题

                ```bash
                # 这是注释，不是标题
                echo hello
                ```
                """;

        List<MarkdownChunker.ArticleChunkData> chunks = chunker.split(md);

        assertFalse(chunks.isEmpty());
        for (MarkdownChunker.ArticleChunkData chunk : chunks) {
            assertFalse(chunk.getHeadingPath().contains("这是注释"),
                    "代码块内的 # 不应进入标题路径，实际: " + chunk.getHeadingPath());
        }
    }

    @Test
    @DisplayName("#tag 这类普通文本不应被当作标题")
    void split_shouldNotTreatHashTagAsHeading() {
        String md = "正文里有个 #tag 标签。";

        List<MarkdownChunker.ArticleChunkData> chunks = chunker.split(md);

        assertEquals(1, chunks.size());
        assertEquals("", chunks.get(0).getHeadingPath(), "非标题的 # 不应产生标题路径");
    }

    @Test
    @DisplayName("空内容应返回空列表")
    void split_emptyContent_shouldReturnEmpty() {
        assertTrue(chunker.split(null).isEmpty());
        assertTrue(chunker.split("").isEmpty());
        assertTrue(chunker.split("   \n  ").isEmpty());
    }

    @Test
    @DisplayName("块字符数应与内容长度一致")
    void split_shouldSetCharCount() {
        List<MarkdownChunker.ArticleChunkData> chunks = chunker.split("# 标题\n\n一些内容。");

        assertEquals(1, chunks.size());
        assertEquals(chunks.get(0).getContent().length(), chunks.get(0).getCharCount());
    }
}
