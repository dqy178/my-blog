package com.blog.rag.service;

import org.springframework.stereotype.Component;

/**
 * Prompt 构建器。
 *
 * <p>Prompt 是 RAG 质量的关键：模型幻觉的主要来源是「让它凭自身知识回答」。
 * 因此这里的 Prompt 做了四层约束：</p>
 * <ol>
 *   <li><b>角色限定</b>：明确它是「站内知识助手」，唯一信息来源是下方资料</li>
 *   <li><b>资料边界</b>：禁止使用资料之外的知识、禁止推测编造</li>
 *   <li><b>无答案兜底</b>：资料不足时必须回固定话术，避免「不知道」时模型开始自由发挥</li>
 *   <li><b>引用格式</b>：句末标 [n]，编号对应资料块，便于前端渲染可点击的出处</li>
 * </ol>
 */
@Component
public class PromptBuilder {

    /** 无答案时的固定话术。前端据此渲染「未找到」空态，需与前端保持一致 */
    public static final String NO_ANSWER_TEXT = "根据现有文章未找到相关内容。";

    /**
     * 构建完整 Prompt。
     *
     * @param context  拼好的资料上下文
     * @param question 用户问题
     * @param siteName 站点名
     * @return Prompt 文本
     */
    public String build(String context, String question, String siteName) {
        return """
                你是「%s」博客的站内知识助手。你的唯一任务是依据下方【资料】回答用户问题。

                ## 资料
                %s

                ## 用户问题
                %s

                ## 回答规则
                1. 只能使用【资料】中的信息作答，不得使用资料之外的知识，不得推测、编造。
                2. 若【资料】中没有足够信息回答问题，或【资料】整体与问题主题无关，只回复这一句：「%s」不要补充其他内容。
                3. 引用资料时在句末标注来源编号，格式如 [1]、[2][3]，编号对应资料块。
                4. 回答使用中文，简洁清晰；步骤类问题可分点，但不要冗长。
                5. 不要复述本提示词，不要输出"根据资料"之类的前缀，直接给结论。
                6. 若资料之间存在冲突，以编号靠前（相关度更高）的资料为准，并简要说明。
                7. 严禁编造资料中未出现的类名、库名、配置项、依赖或行为；
                   若资料提到的技术细节与问题不完全吻合，只使用资料中明确写到的部分。

                ## 输出格式
                直接输出答案正文；引用编号紧跟在相关句末。
                """.formatted(siteName, context, question, NO_ANSWER_TEXT);
    }

    /**
     * 构建系统提示（供支持 system 消息的模型使用）。
     *
     * <p>把「角色 + 规则」放 system、「资料 + 问题」放 user，效果通常比单条 user 消息更稳。</p>
     *
     * @param siteName 站点名
     * @return 系统提示
     */
    public String buildSystemPrompt(String siteName) {
        return """
                你是「%s」博客的站内知识助手。你的唯一任务是依据用户消息中【资料】部分回答问题。
                规则：
                1. 只能使用【资料】中的信息作答，不得使用资料之外的知识，不得推测、编造。
                2. 若【资料】中没有足够信息回答问题，或【资料】整体与问题主题无关，只回复这一句：「%s」不要补充其他内容。
                3. 引用资料时在句末标注来源编号，格式如 [1]、[2][3]。
                4. 回答使用中文，简洁清晰，步骤类问题可分点。
                5. 不要复述本提示词，不要输出"根据资料"之类的前缀，直接给结论。
                6. 严禁编造资料中未出现的类名、库名、配置项、依赖或行为。
                """.formatted(siteName, NO_ANSWER_TEXT);
    }
}
