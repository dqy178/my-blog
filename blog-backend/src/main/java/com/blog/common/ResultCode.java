package com.blog.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务状态码枚举。
 *
 * <p>code 的取值与 HTTP 状态码保持一致，便于前端拦截器直接按 status 分支处理
 * （401 跳登录、403 提示无权限、409 提示冲突等），也便于
 * {@code GlobalExceptionHandler} 把业务异常映射到正确的 HTTP 状态。</p>
 *
 * <p>注意：{@link #FAILED} 与 {@link #SERVER_ERROR} 的 code 同为 500，区别在于
 * 前者表示「业务上的失败」（如操作不满足条件），后者表示「服务器内部错误」。
 * 二者的提示文案不同，接口文档中可据此区分。</p>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {
    /** 操作成功 */
    SUCCESS(200, "操作成功"),
    /** 业务失败（服务端处理正常，但结果不满足预期） */
    FAILED(500, "操作失败"),
    /** 参数校验失败：@Valid 校验未通过或业务参数非法 */
    VALIDATE_FAILED(400, "参数校验失败"),
    /** 未登录、token 无效或已过期 */
    UNAUTHORIZED(401, "未登录或token已过期"),
    /** 已登录但角色不匹配（如普通用户访问 /admin/**） */
    FORBIDDEN(403, "没有相关权限"),
    /** 目标资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 资源被引用导致操作冲突（如删除仍被文章引用的分类） */
    CONFLICT(409, "资源被引用，操作冲突"),
    /** 触发限流（登录失败过多、评论过于频繁） */
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    /** 服务器内部错误 */
    SERVER_ERROR(500, "服务器内部错误");

    /** 业务状态码，与 HTTP 状态码取值一致 */
    private final Integer code;

    /** 默认提示文案，可被更具体的信息覆盖 */
    private final String message;
}