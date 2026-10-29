package com.blog.rag.llm;

/**
 * 大模型客户端抽象。
 *
 * <p>把「调用哪个模型」与「怎么组织 RAG 流程」解耦：上层 {@code RagAskService} 只依赖本接口，
 * 因此 mock（本地演示）与真实模型（OpenAI 兼容协议）可以自由切换。</p>
 */
public interface LlmClient {

    /**
     * 生成回答。
     *
     * @param systemPrompt 系统提示（角色与规则）
     * @param userPrompt   用户提示（资料 + 问题）
     * @return 模型生成的回答文本
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * 客户端名称，用于日志与调试。
     *
     * @return 名称
     */
    String name();
}
