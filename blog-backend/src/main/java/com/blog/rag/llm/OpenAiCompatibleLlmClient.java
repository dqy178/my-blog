package com.blog.rag.llm;

import com.blog.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenAI 兼容协议的大模型客户端。
 *
 * <p>兼容 OpenAI Chat Completions 协议，因此 DeepSeek、MiniMax、通义千问（兼容模式）、
 * 月之暗面、Ollama 的 OpenAI 端点等都能直接接入，只需换 base-url 与 model。</p>
 *
 * <p>使用 Spring 6.1 自带的 {@link RestClient}（spring-boot-starter-web 已包含，
 * 无需额外依赖），同步阻塞调用。</p>
 *
 * <p><b>两处兼容性处理</b>，都是接真实模型时踩出来的：</p>
 * <ul>
 *   <li><b>推理模型（reasoning）的响应结构</b>：带推理能力的模型有两种返回形态 ——
 *       答案与思考过程分离（{@code message.content} 是干净答案，
 *       {@code message.reasoning_content} 是思考过程），
 *       或思考过程混在 {@code content} 里（开头是 {@code <think>...</think>}）。
 *       见 {@link #parseAnswer(String)}。</li>
 *   <li><b>网络瞬时故障的重试</b>：外部模型服务偶发 {@code Connection refused}
 *       或超时，直接失败体验很差。见 {@link #chat(String, String)}。</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.provider", havingValue = "openai")
public class OpenAiCompatibleLlmClient implements LlmClient {

    /** RAG 配置：base-url、model、api-key、超时、温度等 */
    private final RagProperties properties;

    /** 预配置好的 HTTP 客户端（baseUrl 与 Authorization 头已固定） */
    private final RestClient restClient;

    /**
     * @param properties RAG 配置
     */
    public OpenAiCompatibleLlmClient(RagProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(clientRequestFactory())
                .build();
    }

    /** 单次请求的最大尝试次数（含首次），失败时用于重试 */
    private static final int MAX_ATTEMPTS = 3;

    /** 重试间隔（毫秒） */
    private static final long RETRY_INTERVAL_MS = 600;

    /**
     * 调用大模型，失败时自动重试。
     *
     * <p>重试只针对<b>网络类瞬时故障</b>（连接被拒、超时、IO 异常）；
     * 「响应格式不对」属于请求本身有问题，重试只是白等，直接失败
     * （判定见 {@link #isRetryable(LlmException)}）。</p>
     *
     * @param systemPrompt 系统提示：角色与回答规则
     * @param userPrompt   用户提示：资料 + 问题
     * @return 模型生成的回答文本
     * @throws LlmException 重试耗尽仍失败时抛出
     */
    @Override
    public String chat(String systemPrompt, String userPrompt) {
        LlmException lastError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String body = """
                        {
                          "model": "%s",
                          "temperature": %s,
                          "max_tokens": %d,
                          "messages": [
                            {"role": "system", "content": %s},
                            {"role": "user", "content": %s}
                          ]
                        }
                        """.formatted(
                        properties.getModel(),
                        properties.getTemperature(),
                        properties.getMaxTokens(),
                        jsonEscape(systemPrompt),
                        jsonEscape(userPrompt));

                String response = restClient.post()
                        .uri("/chat/completions")
                        .body(body)
                        .retrieve()
                        .body(String.class);

                return parseAnswer(response);
            } catch (LlmException e) {
                lastError = e;
                // 响应格式不对属于「请求本身有问题」，重试也不会变好，直接失败
                if (!isRetryable(e)) {
                    throw e;
                }
            } catch (Exception e) {
                // 网络抖动、连接被拒、超时等瞬时故障值得重试
                lastError = new LlmException("调用大模型失败：" + e.getMessage(), e);
                log.warn("[RAG] 调用大模型第 {}/{} 次失败：{}", attempt, MAX_ATTEMPTS, e.getMessage());
            }

            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("[RAG] 调用大模型重试 {} 次仍失败：{}", MAX_ATTEMPTS, lastError == null ? "unknown" : lastError.getMessage());
        throw lastError != null ? lastError : new LlmException("调用大模型失败");
    }

    /**
     * 判断异常是否值得重试。
     *
     * <p>网络类故障（连接被拒、超时、IO 异常）重试有意义；
     * 而「响应格式不对」说明请求本身有问题，重试只是白等。</p>
     *
     * @param e 异常
     * @return 可重试返回 true
     */
    private boolean isRetryable(LlmException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return !(msg.contains("缺少") || msg.contains("解析失败"));
    }

    @Override
    public String name() {
        return properties.getProvider() + ":" + properties.getModel();
    }

    /**
     * 解析 OpenAI 兼容响应，取出最终答案。
     *
     * <p><b>推理模型（reasoning）的兼容处理</b>：带推理能力的模型（如 MiniMax M3 系列）
     * 有两种返回形态：</p>
     * <ul>
     *   <li>思考过程与答案分离：{@code message.content} 是干净答案，
     *       {@code message.reasoning_content} 是思考过程（MiniMax-M3.1-Flash 属于这种）</li>
     *   <li>思考过程混在答案里：{@code message.content} 开头是 {@code <think>...</think>}
     *       （MiniMax-M3 属于这种）</li>
     * </ul>
     *
     * <p>因此这里做三层处理：content 为空时回退 reasoning_content；
     * 再剥掉可能残留在 content 里的 {@code <think>} 标签；最后去掉思考后的空行。</p>
     *
     * @param response 响应 JSON
     * @return 回答文本
     */
    private String parseAnswer(String response) {
        if (response == null || response.isBlank()) {
            throw new LlmException("大模型返回为空");
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(response);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new LlmException("大模型响应缺少 choices 字段");
            }
            JsonNode message = choices.get(0).get("message");
            if (message == null || message.isNull()) {
                throw new LlmException("大模型响应缺少 message 字段");
            }

            String content = text(message, "content");
            String reasoning = text(message, "reasoning_content");

            // content 为空但有思考内容时回退（说明 max_tokens 可能不够，答案没生成完）
            if (content.isBlank() && !reasoning.isBlank()) {
                log.warn("[RAG] 模型未返回 content，回退使用 reasoning_content（可能 max_tokens 不足）");
                content = reasoning;
            }
            if (content.isBlank()) {
                throw new LlmException("大模型响应缺少 message.content");
            }

            // 剥离混入答案里的思考标签
            String cleaned = content.replaceAll("(?s)<think\\s*>.*?</think\\s*>", "").trim();
            return cleaned.isBlank() ? content.trim() : cleaned;
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("解析大模型响应失败：" + e.getMessage(), e);
        }
    }

    /**
     * 取节点中的文本内容。
     *
     * @param node 父节点
     * @param field 字段名
     * @return 文本；节点缺失或为 null 时返回空串
     */
    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    /**
     * 转义 JSON 字符串中的特殊字符。
     *
     * @param text 原始文本
     * @return 转义后的文本（不含引号）
     */
    private String jsonEscape(String text) {
        if (text == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * 构建带超时的请求工厂。
     *
     * @return 请求工厂
     */
    private org.springframework.http.client.ClientHttpRequestFactory clientRequestFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        return factory;
    }
}
