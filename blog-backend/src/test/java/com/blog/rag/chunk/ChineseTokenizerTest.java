package com.blog.rag.chunk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中文分词器单元测试。
 *
 * <p>重点覆盖：中文 bigram 切分、英文单词切分、停用词过滤、
 * 以及「缓存穿透」这类词能否被 bigram 覆盖到。</p>
 */
class ChineseTokenizerTest {

    private final ChineseTokenizer tokenizer = new ChineseTokenizer();

    @Test
    @DisplayName("中文应切成单字与 bigram")
    void tokenize_shouldSplitChineseIntoBigram() {
        List<String> tokens = tokenizer.tokenize("缓存穿透");

        assertTrue(tokens.contains("缓"), "应包含单字「缓」");
        assertTrue(tokens.contains("缓存"), "应包含 bigram「缓存」");
        assertTrue(tokens.contains("存穿"), "应包含 bigram「存穿」");
        assertTrue(tokens.contains("穿透"), "应包含 bigram「穿透」");
    }

    @Test
    @DisplayName("英文应按单词切分并转小写")
    void tokenize_shouldSplitEnglishWords() {
        List<String> tokens = tokenizer.tokenize("Redis Cache 失效");

        assertTrue(tokens.contains("redis"), "英文应转小写");
        assertTrue(tokens.contains("cache"));
        assertFalse(tokens.contains("Redis"), "英文不应保留原大小写");
    }

    @Test
    @DisplayName("停用词应被过滤")
    void tokenize_shouldRemoveStopWords() {
        List<String> tokens = tokenizer.tokenize("的了和是在");

        assertTrue(tokens.isEmpty(), "纯停用词应得到空结果，实际: " + tokens);
    }

    @Test
    @DisplayName("标点与空白应被忽略")
    void tokenize_shouldIgnorePunctuation() {
        List<String> tokens = tokenizer.tokenize("你好，世界！这是一个测试。");

        assertFalse(tokens.contains("，"), "不应包含标点");
        assertFalse(tokens.contains("！"), "不应包含标点");
        assertTrue(tokens.contains("测试"), "应保留实词");
    }

    @Test
    @DisplayName("单字中文应作为独立词元")
    void tokenize_shouldKeepSingleChar() {
        List<String> tokens = tokenizer.tokenize("锁");

        assertTrue(tokens.contains("锁"), "单字查询要能召回");
    }

    @Test
    @DisplayName("空输入应返回空结果")
    void tokenize_emptyInput_shouldReturnEmpty() {
        assertTrue(tokenizer.tokenize(null).isEmpty());
        assertTrue(tokenizer.tokenize("").isEmpty());
        assertTrue(tokenizer.tokenize("   ").isEmpty());
    }

    @Test
    @DisplayName("词元不应重复")
    void tokenize_shouldNotDuplicate() {
        List<String> tokens = tokenizer.tokenize("缓存缓存");

        long cacheCount = tokens.stream().filter("缓存"::equals).count();
        assertEquals(1, cacheCount, "相同词元只应出现一次");
    }

    @Test
    @DisplayName("含停用字的 bigram 应被丢弃")
    void tokenize_shouldDropBigramContainingStopWord() {
        List<String> tokens = tokenizer.tokenize("的是项目");

        assertFalse(tokens.contains("的是"), "含停用字「的」的 bigram 应丢弃");
        assertTrue(tokens.contains("项目"), "不含停用字的 bigram 应保留");
    }

    @Test
    @DisplayName("疑问字组成的 bigram 应被丢弃")
    void tokenize_shouldDropBigramContainingQuestionWord() {
        List<String> tokens = tokenizer.tokenize("怎么样啊");

        assertFalse(tokens.contains("怎么"), "含疑问字「怎」的 bigram 应丢弃");
        assertFalse(tokens.contains("么样"), "含疑问字「么」的 bigram 应丢弃");
    }
}
