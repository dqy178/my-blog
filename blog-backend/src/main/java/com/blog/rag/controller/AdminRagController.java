package com.blog.rag.controller;

import com.blog.common.Result;
import com.blog.rag.retrieval.Bm25Retriever;
import com.blog.rag.service.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台检索索引管理接口。
 *
 * <p>路径前缀 {@code /admin/**} 强制要求 {@code ROLE_ADMIN} 角色。</p>
 *
 * <p>提供「全量重建」是必要的：存量文章在功能上线前没有块，
 * 必须重建一次才能被检索到。</p>
 *
 * <p>另外两个接口是运维辅助工具：{@code /models} 列出服务商可用的模型名，
 * {@code /test-llm} 做连通性自检。两者都用于解决「配置写错了但报错不直观」的问题
 * —— 实测中 {@code MiniMax-M2.1} 就出现在模型列表里，却因已下线而调用返回 404。</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/rag")
@RequiredArgsConstructor
public class AdminRagController {

    /** 索引服务：切块落库与内存索引同步 */
    private final IndexService indexService;
    /** BM25 检索器：读取内存索引的统计信息 */
    private final Bm25Retriever retriever;
    /** 大模型客户端：连通性自检 */
    private final com.blog.rag.llm.LlmClient llmClient;
    /** RAG 配置：读取 base-url 与 api-key */
    private final com.blog.config.RagProperties properties;

    /**
     * 全量重建索引。
     *
     * <p>会清空 {@code article_chunk} 表并重新切块所有已发布文章，
     * 同时刷新内存索引。耗时随文章量增长。</p>
     *
     * @return 处理的文章数与块数
     */
    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindexAll() {
        int articles = indexService.rebuildAll();
        Map<String, Object> data = new HashMap<>();
        data.put("articles", articles);
        data.put("chunks", retriever.size());
        return Result.success("索引重建完成", data);
    }

    /**
     * 查看索引状态。
     *
     * @return 索引统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("chunks", retriever.size());
        data.put("averageTermCount", retriever.averageTermCount());
        return Result.success(data);
    }

    /**
     * 大模型连通性自检。
     *
     * <p>用一句固定的短提示调用模型，确认三件事：Key 是否有效、
     * base-url 与 model 是否正确、网络是否可达。返回模型的实际回复与耗时，
     * 便于在切换真实模型后第一时间确认配置无误。</p>
     *
     * @return 模型标识、回复内容与耗时
     */
    @PostMapping("/test-llm")
    public Result<Map<String, Object>> testLlm() {
        Map<String, Object> data = new HashMap<>();
        data.put("model", llmClient.name());
        long start = System.currentTimeMillis();
        try {
            String reply = llmClient.chat(
                    "你是一个连通性测试助手，只回复「ok」两个字。",
                    "请回复 ok");
            data.put("ok", true);
            data.put("reply", reply);
        } catch (Exception e) {
            data.put("ok", false);
            data.put("error", e.getMessage());
        }
        data.put("elapsedMs", System.currentTimeMillis() - start);
        return Result.success(data);
    }

    /**
     * 列出服务端可用的模型。
     *
     * <p>调用 OpenAI 兼容的 {@code GET {base-url}/models}，用于确认
     * {@code RAG_MODEL} 该填什么——不同服务商（DeepSeek / MiniMax / 通义 / Ollama）
     * 的模型名各不相同，写错时的报错信息往往不够直观，直接列出来最省事。</p>
     *
     * <p>仅在 {@code rag.provider=openai} 时可用；mock 模式会提示先配置服务商。</p>
     *
     * @return 可用模型名列表
     */
    @GetMapping("/models")
    public Result<List<String>> models() {
        if (!"openai".equals(properties.getProvider())) {
            return Result.failed(400,
                    "当前 rag.provider=" + properties.getProvider()
                            + "，无法列出模型。请在 .env 里设 RAG_PROVIDER=openai 并配置 RAG_API_KEY");
        }
        try {
            // 单独构造请求工厂以便设置超时：requestFactory 接受的是 ClientHttpRequestFactory
            // 实例（不是工厂函数），所以不能写成 lambda。
            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) properties.getTimeoutMs());
            factory.setReadTimeout(properties.getTimeoutMs());

            String resp = org.springframework.web.client.RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                    .requestFactory(factory)
                    .build()
                    .get()
                    .uri("/models")
                    .retrieve()
                    .body(String.class);

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(resp);
            List<String> names = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode m : root.path("data")) {
                String id = m.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    names.add(id);
                }
            }
            names.sort(String::compareToIgnoreCase);
            return Result.success(names);
        } catch (Exception e) {
            log.error("[RAG] 列出模型失败：{}", e.getMessage());
            return Result.failed(400, "列出模型失败：" + e.getMessage());
        }
    }
}
