package com.blog.rag.chunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 中文友好的轻量分词器。
 *
 * <p>不引入第三方分词库（Jieba / Lucene），而是用「字符二元组（bigram）」方案：
 * 把中文串切成相邻两字的组合，如「缓存穿透」→「缓存」「存穿」「穿透」。</p>
 *
 * <p>选择 bigram 而非完整分词器的原因：</p>
 * <ul>
 *   <li>零依赖，避免为了检索引入一个需要维护词典的中文分词组件</li>
 *   <li>对中文检索效果可用：bigram 天然覆盖了词内组合，
 *       「缓存穿透」与「穿透缓存」能通过共享的 bigram 命中</li>
 *   <li>单字也一并保留，保证短查询（如「锁」）能召回</li>
 * </ul>
 *
 * <p>英文与数字按单词切分并转小写；其余符号一律丢弃。</p>
 */
public class ChineseTokenizer {

    /**
     * 停用词：高频且无检索意义的词。
     *
     * <p>除了常见的虚词，还包含「怎、么、什、哪、几、样」这类疑问单字——
     * 它们几乎出现在每个提问里，却不携带任何主题信息。</p>
     */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "和", "是", "在", "我", "有", "就", "不", "人", "都", "一",
            "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有",
            "看", "好", "自己", "这", "那", "与", "及", "或", "等", "被", "把", "让",
            "从", "对", "为", "以", "而", "但", "并", "而且",
            // 疑问单字：几乎每个提问都含，却不携带主题信息
            "怎", "么", "什", "哪", "几", "样", "呢", "吧", "啊", "吗", "哈", "嗯", "呀", "嘛",
            "the", "a", "an", "is", "are", "of", "to", "in", "on", "and", "or", "for",
            "how", "what", "why", "which", "can", "i", "you", "it", "this", "that", "be", "with"
    ));

    /**
     * 对文本分词。
     *
     * @param text 原始文本
     * @return 去重后的词元列表（保持首次出现顺序）
     */
    public List<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return new ArrayList<>(tokens);
        }
        String lower = text.toLowerCase();

        int len = lower.length();
        int i = 0;
        while (i < len) {
            char c = lower.charAt(i);
            if (isCjk(c)) {
                // 中文：取连续的中文串做 bigram
                int start = i;
                while (i < len && isCjk(lower.charAt(i))) {
                    i++;
                }
                addCjkTokens(lower, start, i, tokens);
            } else if (Character.isLetterOrDigit(c)) {
                // 英文/数字：按单词切
                int start = i;
                while (i < len && Character.isLetterOrDigit(lower.charAt(i))) {
                    i++;
                }
                String word = lower.substring(start, i);
                if (!STOP_WORDS.contains(word) && word.length() > 1) {
                    tokens.add(word);
                }
            } else {
                // 其余字符（标点、空白、Markdown 符号）直接跳过
                i++;
            }
        }
        return new ArrayList<>(tokens);
    }

    /**
     * 把一段连续中文切成单字 + bigram。
     *
     * <p><b>停用词过滤的关键细节</b>：停用词表里只有单字与少量词，
     * 而 bigram 会组合出新词元——「的是」不是停用词，但显然没有检索价值。
     * 因此规则是：<b>bigram 的任意一个字是停用字（虚词或疑问字）就整条丢弃</b>。
     * 这样「的项目」「么怎」「是怎」这类垃圾组合不会进入索引，
     * 否则任何提问都能靠它们召回一批无关文档。</p>
     *
     * @param s      文本
     * @param from   起始下标（含）
     * @param to     结束下标（不含）
     * @param tokens 结果容器
     */
    private void addCjkTokens(String s, int from, int to, Set<String> tokens) {
        int n = to - from;
        if (n == 1) {
            // 单字单独成词（多见于标题、短查询）
            addToken(s.substring(from, to), tokens);
            return;
        }
        for (int i = from; i < to; i++) {
            addToken(s.substring(i, i + 1), tokens);
            if (i + 2 <= to) {
                String bigram = s.substring(i, i + 2);
                // 任意一个字是停用字，这个 bigram 就没有检索价值
                if (!STOP_WORDS.contains(bigram.substring(0, 1))
                        && !STOP_WORDS.contains(bigram.substring(1, 2))) {
                    addToken(bigram, tokens);
                }
            }
        }
    }

    /**
     * 加入词元，过滤停用词。
     *
     * @param token  词元
     * @param tokens 结果容器
     */
    private void addToken(String token, Set<String> tokens) {
        if (!STOP_WORDS.contains(token)) {
            tokens.add(token);
        }
    }

    /**
     * 判断是否为中日韩统一表意文字。
     *
     * @param c 字符
     * @return 是中文字符返回 true
     */
    private boolean isCjk(char c) {
        return (c >= '一' && c <= '鿿')
                || (c >= '㐀' && c <= '䶿')
                || (c >= '぀' && c <= 'ヿ')
                || (c >= '가' && c <= '힯');
    }

    /**
     * 判断是否为停用词。
     *
     * @param token 词元
     * @return 是停用词返回 true
     */
    public boolean isStopWord(String token) {
        return STOP_WORDS.contains(token);
    }

    /**
     * 获取停用词集合（只读）。
     *
     * @return 停用词集合
     */
    public Set<String> getStopWords() {
        return Set.copyOf(STOP_WORDS);
    }
}
