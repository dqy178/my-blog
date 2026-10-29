package com.blog.rag.llm;

/**
 * 大模型调用异常。
 */
public class LlmException extends RuntimeException {

    /**
     * @param message 错误信息
     */
    public LlmException(String message) {
        super(message);
    }

    /**
     * @param message 错误信息
     * @param cause   原始异常
     */
    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
