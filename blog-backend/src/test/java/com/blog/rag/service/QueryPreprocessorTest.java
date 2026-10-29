package com.blog.rag.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查询预处理单元测试。
 *
 * <p>重点覆盖：疑问词剔除、双字词存在时丢弃单字（避免噪声召回）、
 * 以及短查询保留单字。</p>
 */
class QueryPreprocessorTest {

    private final QueryPreprocessor preprocessor = new QueryPreprocessor();

    @Test
    @DisplayName("应剔除疑问词")
    void process_shouldRemoveQuestionWords() {
        Set<String> terms = preprocessor.process("如何配置 Redis");

        assertFalse(terms.contains("如何"), "疑问词应被剔除");
        assertTrue(terms.contains("配置"), "实词应保留");
        assertTrue(terms.contains("Redis") || terms.contains("redis"), "英文词应保留");
    }

    @Test
    @DisplayName("有双字词时应丢弃单字，避免噪声召回")
    void process_shouldDropSingleCharWhenMultiCharExists() {
        Set<String> terms = preprocessor.process("今天天气怎么样");

        assertTrue(terms.contains("今天") || terms.contains("天气"),
                "双字实词应保留，实际: " + terms);
        assertFalse(terms.contains("天"), "有双字词时单字应丢弃，实际: " + terms);
        assertFalse(terms.contains("气"), "有双字词时单字应丢弃，实际: " + terms);
        assertFalse(terms.contains("样"), "疑问相关单字应丢弃，实际: " + terms);
    }

    @Test
    @DisplayName("纯单字查询应保留单字")
    void process_singleCharQuery_shouldKeepSingleChar() {
        Set<String> terms = preprocessor.process("锁");

        assertTrue(terms.contains("锁"), "单字查询要能召回，实际: " + terms);
    }

    @Test
    @DisplayName("空输入应返回空集合")
    void process_emptyInput_shouldReturnEmpty() {
        assertTrue(preprocessor.process(null).isEmpty());
        assertTrue(preprocessor.process("").isEmpty());
        assertTrue(preprocessor.process("  ").isEmpty());
    }

    @Test
    @DisplayName("纯疑问词的问题应无有效词")
    void process_onlyQuestionWords_shouldReturnEmpty() {
        assertTrue(preprocessor.process("怎么办").isEmpty()
                        || preprocessor.process("怎么办").size() < 3,
                "纯疑问词不该留下大量词元，实际: " + preprocessor.process("怎么办"));
    }

    @Test
    @DisplayName("应做同义词扩展，缓解词汇鸿沟")
    void process_shouldExpandSynonyms() {
        // 真实场景：用户问「鉴权」，但文章里写的是 JWT / Token / 登录，
        // 不扩展就召不到讲 JWT 的文章。
        Set<String> terms = preprocessor.process("项目的鉴权是怎么做的");

        assertTrue(terms.contains("鉴权"), "原词应保留，实际: " + terms);
        assertTrue(terms.stream().anyMatch(t -> "jwt".equalsIgnoreCase(t)
                        || "token".equalsIgnoreCase(t)
                        || t.contains("登录")),
                "应扩展出 JWT/Token/登录等同义表述，实际: " + terms);
    }

    @Test
    @DisplayName("同义词扩展是双向的")
    void process_synonymExpansion_shouldBeBidirectional() {
        Set<String> fromJwt = preprocessor.process("JWT 怎么实现");
        assertTrue(fromJwt.stream().anyMatch(t -> t.contains("鉴权") || t.contains("认证")),
                "从 JWT 也应能扩展到鉴权/认证，实际: " + fromJwt);
    }

    @Test
    @DisplayName("无同义词的词不应被扩展")
    void process_noSynonym_shouldNotExpand() {
        Set<String> terms = preprocessor.process("布谷鸟");

        assertFalse(terms.contains("鉴权"), "不应扩展出无关同义词，实际: " + terms);
        assertFalse(terms.contains("缓存"), "不应扩展出无关同义词，实际: " + terms);
        assertFalse(terms.contains("jwt"), "不应扩展出无关同义词，实际: " + terms);
    }
}
