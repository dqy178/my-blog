package com.blog.rag.llm;

import com.blog.rag.chunk.ChineseTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地模拟的大模型客户端。
 *
 * <p><b>用途</b>：在没有 API Key 的情况下跑通 RAG 全链路，用于本地演示、
 * 单元测试与答辩前的自检。</p>
 *
 * <p><b>它不是真的语言模型</b>：只是从资料里挑出与问题最相关的句子，
 * 按模板拼成一段带引用编号的回答。这样能真实反映「检索是否召回正确」，
 * 从而验证整条链路（切块 → 检索 → 上下文 → 引用编号 → 前端渲染）是否通。</p>
 *
 * <p><b>能验证什么、不能验证什么</b>：</p>
 * <ul>
 *   <li>能：切块是否合理、BM25 排序是否准确、上下文拼装与编号是否正确、
 *       检索不到时是否诚实兜底</li>
 *   <li>不能：生成质量、指令遵循、长上下文下的答案表现、幻觉率
 *       —— 这些必须接真实模型才能评估</li>
 * </ul>
 *
 * <p>实现上刻意<b>信任检索器的排序</b>：上下文里块的编号顺序就是 BM25 的相关度排名，
 * 所以直接取第一个块，不再自己按关键词命中次数重排（那样会系统性偏向长文档）。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    /**
     * 资料块的编号标记与标题，如 {@code [1] 《文章标题》}。
     *
     * <p>注意这里的转义：{@code \\[(\\d+)]} 匹配的是 {@code [1]}，
     * 后面不能再多写一个 {@code \\]}——那会要求匹配 {@code [1]]}，导致解析不到任何块。</p>
     */
    private static final Pattern BLOCK_PATTERN =
            Pattern.compile("\\[(\\d+)]\\s*《([^》]*)》[^\\n]*\\n([\\s\\S]*?)(?=\\n\\[\\d+]|\\z)");

    /**
     * 无答案时的固定话术。与 {@code PromptBuilder.NO_ANSWER_TEXT} 保持一致。
     */
    private static final String NO_ANSWER = "根据现有文章未找到相关内容。";

    /**
     * 生成一段模拟回答。
     *
     * <p>流程：从 Prompt 里抠出「问题」与「资料」→ 解析出资料块 →
     * 取排名最高的块 → 在块里挑出含关键词的句子 → 拼上引用编号。</p>
     *
     * @param systemPrompt 系统提示（本实现不使用）
     * @param userPrompt   用户提示，含「## 资料」与「## 用户问题」两段
     * @return 带引用编号的回答；检索不到时返回固定兜底话术
     */
    @Override
    public String chat(String systemPrompt, String userPrompt) {
        String question = extractQuestion(userPrompt);
        String context = extractContext(userPrompt);

        if (context.isBlank()) {
            return NO_ANSWER;
        }

        // 抽取资料块。块的编号顺序即 BM25 的相关度排名
        // （ContextBuilder 按检索得分降序排列后编号），因此 blocks.get(0) 就是最相关的块。
        //
        // 这里刻意「信任检索器的排序」而不再自己按命中次数重排：
        // 朴素的命中计数没有长度归一化，会系统性地偏向长文档
        // （长文档包含的词元种类天然更多），这正是 BM25 要解决的问题。
        List<ContentBlock> blocks = parseBlocks(context);
        if (blocks.isEmpty()) {
            return NO_ANSWER;
        }

        // 用分词后的词元挑句子，而不是整句 substring：
        // 中文问题没有空格，整句匹配几乎必然失败。
        List<String> terms = new ChineseTokenizer().tokenize(question);

        ContentBlock best = blocks.get(0);
        String sent = pickSentence(best.content(), terms);
        if (sent.isBlank()) {
            return NO_ANSWER;
        }

        StringBuilder answer = new StringBuilder();
        answer.append(sent).append('[').append(best.index()).append(']');

        // 补充第二个相关块（若有），让出处列表不只一条
        for (ContentBlock block : blocks) {
            if (block.index() == best.index()) {
                continue;
            }
            String sent2 = pickSentence(block.content(), terms);
            if (!sent2.isBlank() && !sent2.equals(sent)) {
                answer.append('\n').append(sent2).append('[').append(block.index()).append(']');
                break;
            }
        }

        log.info("[RAG][mock] 生成回答，命中块 {}，问题「{}」", best.index(), question);
        return answer.toString();
    }

    @Override
    public String name() {
        return "mock";
    }

    /**
     * 从 user prompt 中取出「## 用户问题」之后的内容。
     *
     * @param userPrompt 用户提示
     * @return 问题文本
     */
    private String extractQuestion(String userPrompt) {
        int idx = userPrompt.indexOf("## 用户问题");
        if (idx < 0) {
            return "";
        }
        String rest = userPrompt.substring(idx + "## 用户问题".length());
        int end = rest.indexOf("## 回答规则");
        if (end < 0) {
            end = rest.indexOf("\n\n");
        }
        return (end > 0 ? rest.substring(0, end) : rest).trim();
    }

    /**
     * 从 user prompt 中取出「## 资料」之后、「## 用户问题」之前的内容。
     *
     * @param userPrompt 用户提示
     * @return 资料文本
     */
    private String extractContext(String userPrompt) {
        int start = userPrompt.indexOf("## 资料");
        int end = userPrompt.indexOf("## 用户问题");
        if (start < 0 || end < 0 || end <= start) {
            return "";
        }
        return userPrompt.substring(start + "## 资料".length(), end).trim();
    }

    /**
     * 解析资料块。
     *
     * @param context 资料文本
     * @return 块列表
     */
    private java.util.List<ContentBlock> parseBlocks(String context) {
        java.util.List<ContentBlock> blocks = new java.util.ArrayList<>();
        Matcher m = BLOCK_PATTERN.matcher(context);
        while (m.find()) {
            blocks.add(new ContentBlock(
                    Integer.parseInt(m.group(1)),
                    m.group(2),
                    m.group(3).trim()));
        }
        return blocks;
    }

    /**
     * 从内容里挑出包含关键词的句子。
     *
     * <p>优先用长度 ≥2 的词元（bigram）：单字词元过于宽泛，
     * 几乎任何中文块都能命中，反而会挑出与主题无关的句子。</p>
     *
     * <p>双字词元都匹配不上时，再退化到单字词元（应对「锁」「线程」这类短查询）。
     * 两者都匹配不上则返回空串，让上层判定为「未找到」——
     * <b>绝不回退到「返回内容开头」</b>，那会让无关问题也得到一段看似有理的答案。</p>
     *
     * @param content 内容
     * @param terms   关键词数组
     * @return 句子；无匹配时为空串
     */
    private String pickSentence(String content, List<String> terms) {
        String flat = content.replaceAll("\\s+", " ").trim();
        String sentence = matchSentence(flat, terms, 2);
        if (!sentence.isBlank()) {
            return sentence;
        }
        return matchSentence(flat, terms, 1);
    }

    /**
     * 在文本中找包含指定长度以上关键词的句子。
     *
     * @param flat        已压平空白的文本
     * @param terms       关键词数组
     * @param minTermLen  关键词最小长度
     * @return 句子；无匹配时为空串
     */
    private String matchSentence(String flat, List<String> terms, int minTermLen) {
        for (String term : terms) {
            if (term == null || term.length() < minTermLen) {
                continue;
            }
            int idx = flat.toLowerCase().indexOf(term.toLowerCase());
            if (idx >= 0) {
                // 截取包含关键词的句子
                int start = flat.lastIndexOf('。', idx);
                start = start >= 0 ? start + 1 : Math.max(0, idx - 40);
                int end = flat.indexOf('。', idx);
                end = end >= 0 ? end + 1 : Math.min(flat.length(), idx + 120);
                String sentence = flat.substring(start, end).trim();
                if (sentence.length() > 10) {
                    return sentence;
                }
            }
        }
        return "";
    }

    /**
     * 资料块。
     *
     * @param index   编号
     * @param title   标题
     * @param content 内容
     */
    private record ContentBlock(int index, String title, String content) {
    }
}
