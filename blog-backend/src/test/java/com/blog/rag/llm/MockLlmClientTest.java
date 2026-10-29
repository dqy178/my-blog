package com.blog.rag.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mock 大模型客户端单元测试。
 *
 * <p>重点覆盖：资料块的解析（编号正则）、无关问题返回兜底话术、
 * 以及答案中必须带引用编号。</p>
 *
 * <p>这个测试类的存在意义：曾经因为资料块编号正则多写了一个 {@code ]}
 * （实际匹配的是 {@code [1]]} 而非 {@code [1]}），导致解析不到任何块、
 * 所有问题都返回「未找到」。这类问题纯靠编译和类型检查发现不了，
 * 必须有端到端的解析测试兜住。</p>
 */
class MockLlmClientTest {

    private final MockLlmClient client = new MockLlmClient();

    /**
     * 构造一段与 PromptBuilder 一致的资料上下文。
     *
     * @return 上下文文本
     */
    private String context() {
        return """
                [1] 《Redis 缓存实战》>基础 > 缓存穿透
                缓存穿透是指查询一个根本不存在的数据。解决办法是缓存空值并设置较短的过期时间。

                [2] 《MySQL 索引原理》>索引 > B+树
                B+树是一种多路平衡查找树，叶子节点之间用链表连接，适合范围查询。
                """;
    }

    /**
     * 构造与 PromptBuilder 一致的用户提示。
     *
     * @param context  上下文
     * @param question 问题
     * @return 用户提示
     */
    private String userPrompt(String context, String question) {
        return """
                ## 资料
                %s

                ## 用户问题
                %s

                ## 回答规则
                1. 只能使用【资料】中的信息作答。
                """.formatted(context, question);
    }

    @Test
    @DisplayName("应能解析出资料块并生成带引用编号的回答")
    void chat_shouldParseBlocksAndAddCitation() {
        String answer = client.chat("", userPrompt(context(), "缓存穿透怎么解决"));

        assertFalse(answer.isBlank(), "不应返回空回答");
        assertFalse(answer.contains("根据现有文章未找到相关内容"),
                "有资料时不应返回未找到话术，实际: " + answer);
        assertTrue(answer.contains("[1]"), "答案应带引用编号，实际: " + answer);
    }

    @Test
    @DisplayName("资料为空时应返回兜底话术")
    void chat_emptyContext_shouldReturnFallback() {
        String answer = client.chat("", userPrompt("", "缓存穿透怎么解决"));

        assertEquals("根据现有文章未找到相关内容。", answer);
    }

    @Test
    @DisplayName("资料格式被破坏（编号后多一个方括号）时也应能解析")
    void chat_shouldTolerateMalformedNumbering() {
        // 模拟曾经的 bug：编号被写成 [1]]，解析器应仍然工作或至少不抛异常
        String broken = """
                [1]] 《Redis 缓存实战》>基础
                缓存穿透是指查询不存在的数据。

                [2]] 《MySQL 索引原理》>索引
                B+树适合范围查询。
                """;
        String answer = client.chat("", userPrompt(broken, "缓存穿透怎么解决"));

        assertFalse(answer.isBlank());
    }

    @Test
    @DisplayName("问题与资料完全无关时返回未找到话术")
    void chat_unrelatedQuestion_shouldReturnFallback() {
        // 资料里讲的是缓存与索引，问题问天气，二者没有任何共同的实词，
        // 此时必须返回未找到话术，而不是从无关块里硬凑一段答案。
        String answer = client.chat("", userPrompt(context(), "今天天气怎么样"));

        assertEquals("根据现有文章未找到相关内容。", answer,
                "与资料无关的问题应返回未找到话术，实际: " + answer);
    }

    @Test
    @DisplayName("应优先引用 BM25 排名靠前（编号小）的块")
    void chat_shouldCiteHighestRankedBlockFirst() {
        // 上下文里 [1] 是 BM25 认为最相关的块（Redis 缓存穿透），[2] 是 MySQL 索引。
        // 问缓存相关问题时，答案首句必须引用 [1]。
        String answer = client.chat("", userPrompt(context(), "缓存穿透怎么解决"));

        int first = answer.indexOf('[');
        assertTrue(first >= 0, "答案应带引用编号，实际: " + answer);
        assertEquals('1', answer.charAt(first + 1),
                "首个引用应是编号 1（BM25 排名最高），实际: " + answer);
    }

    @Test
    @DisplayName("客户端名称应为 mock")
    void name_shouldReturnMock() {
        assertEquals("mock", client.name());
    }
}
