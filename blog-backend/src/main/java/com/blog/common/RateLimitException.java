package com.blog.common;

/**
 * 限流异常。
 *
 * <p>与普通业务异常区分开，便于控制器映射到 429（Too Many Requests），
 * 让前端能识别为「稍后重试」而不是「请求有误」。</p>
 */
public class RateLimitException extends RuntimeException {

    /**
     * @param message 提示文案
     */
    public RateLimitException(String message) {
        super(message);
    }
}
