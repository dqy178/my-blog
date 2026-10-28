package com.blog.common;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>用于表达「输入合法但业务条件不满足」这类可预期的失败，例如文章不存在、
 * 分类仍被引用、状态值非法。抛出后由 {@code GlobalExceptionHandler} 统一捕获，
 * 按 {@link #getCode()} 映射 HTTP 状态码并把 message 原样返回给前端。</p>
 *
 * <p>与运行时异常的区别：本异常代表正常业务流程的一部分，不会被记为系统错误日志
 * （处理器中用 log.warn 记录），而未预期的异常会走 500 兜底分支并记录完整堆栈。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码，见 {@link ResultCode} */
    private final Integer code;

    /** 使用默认的「操作失败」code（500） */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.FAILED.getCode();
    }

    /** 指定 code 与自定义文案 */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /** 使用枚举中预定义的 code 与文案 */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /** 使用枚举的 code，但覆盖提示文案 */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}