package com.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 相关配置。
 *
 * <p>全部项支持环境变量覆盖（{@code RAG_*} 前缀），生产环境通过
 * {@code application-prod.yml} 的 {@code ${ENV:default}} 写法注入。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** 是否启用 RAG 问答功能。关闭时 /ask 接口直接返回「功能未启用」 */
    private boolean enabled = true;

    /**
     * 大模型调用方式：{@code mock} 或 {@code openai}。
     *
     * <p>mock 模式不调用外部服务，基于检索到的上下文按模板生成答案，
     * 用于无 API Key 时的本地演示与单元测试。</p>
     */
    private String provider = "mock";

    /** OpenAI 兼容接口地址，如 {@code https://api.deepseek.com/v1} */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** 模型名 */
    private String model = "deepseek-chat";

    /** API Key。mock 模式不需要 */
    private String apiKey = "";

    /** 单次请求超时（毫秒） */
    private int timeoutMs = 60000;

    /** 采样温度。RAG 场景建议低值（0.1~0.3），保证答案稳定可复现 */
    private double temperature = 0.2;

    /** 生成的最大 token 数 */
    private int maxTokens = 1024;

    /** 检索返回的块数量上限（Top-K） */
    private int topK = 5;

    /** 候选块数量上限：先粗排取 topK * candidateMultiplier，再精排取 topK */
    private int candidateMultiplier = 4;

    /** 拼入上下文的最大字符数，超出则截断（控制 token 成本） */
    private int contextMaxChars = 4000;

    /** 单个块的最大字符数。超出则在段落边界二次切分 */
    private int chunkMaxChars = 500;

    /** 单个块的最小字符数。过短的块直接并入相邻块，避免碎片化 */
    private int chunkMinChars = 80;

    /** 相邻块的重叠字符数。避免语义在块边界被切断 */
    private int chunkOverlapChars = 100;

    /**
     * 单个 IP 每分钟最多提问次数。
     *
     * <p>默认放宽到 20：连续探索与调试时很容易连问十几次，
     * 5 次/分钟会误伤正常使用。已登录的管理员完全不受此限制。</p>
     */
    private int rateLimitPerMinute = 20;

    /**
     * 单个 IP 每天最多提问次数（控制大模型调用成本）。
     *
     * <p>默认 500。管理员同样豁免。</p>
     */
    private int dailyLimit = 500;

    /**
     * 相关性阈值比例：只保留得分不低于「最高分 × 该比例」的块。
     *
     * <p>BM25 只过滤掉「一分不得」的块，弱相关块仍会进入上下文，
     * 模型可能据此拼凑出「拼接式幻觉」。默认 0.5 表示保留得分在最高分一半以上的块。</p>
     */
    private double relevanceRatio = 0.5;

    /**
     * 启动时校验配置的合法性。
     *
     * <p>provider 只允许 mock / openai 两个值。若配成别的（比如直接写模型名 "deepseek"），
     * 会导致没有任何 {@code LlmClient} Bean 被装配，启动时报「No qualifying bean」——
     * 那是个很难定位的错。这里提前给出明确提示。</p>
     */
    @jakarta.annotation.PostConstruct
    public void validate() {
        String p = provider == null ? "" : provider.trim().toLowerCase();
        if (!"mock".equals(p) && !"openai".equals(p)) {
            throw new IllegalStateException(
                    "rag.provider 配置无效：" + provider
                            + "。只支持 mock（本地模拟）或 openai（OpenAI 兼容协议）。"
                            + "例如使用 DeepSeek：RAG_PROVIDER=openai");
        }
        this.provider = p;
    }
}
