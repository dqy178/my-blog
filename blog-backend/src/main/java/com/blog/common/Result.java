package com.blog.common;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应包装。
 *
 * <p>所有控制器方法的返回值都包装成该结构。前端 {@code src/utils/request.ts} 的响应
 * 拦截器按 {@code code === 200} 判断业务成功并取出 {@code data} 字段，因此
 * {@code code / message / data} 三个字段名属于前后端契约，不可随意改名。</p>
 *
 * <p>用法：通过 {@link #success()} / {@link #success(Object)} / {@link #failed(String)} 等
 * 静态工厂方法创建，不要直接 new —— 字段的 code 与 message 组合有固定语义。</p>
 *
 * @param <T> 业务负载类型
 */
@Data
@NoArgsConstructor
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码，取值见 {@link ResultCode}。200 表示成功，其余表示各类失败 */
    private Integer code;

    /** 提示文案。成功时为「操作成功」，失败时为可直接展示给用户的失败原因 */
    private String message;

    /** 业务负载。失败时为 null */
    private T data;

    /**
     * 私有构造：统一通过静态工厂方法创建，避免调用方 new 出字段语义不完整的实例。
     */
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功，无业务负载 */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /** 成功，携带业务负载 */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 成功，携带自定义提示文案与业务负载 */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /** 失败，使用默认的「操作失败」文案 */
    public static <T> Result<T> failed() {
        return new Result<>(ResultCode.FAILED.getCode(), ResultCode.FAILED.getMessage(), null);
    }

    /** 失败，使用自定义提示文案（code 为默认的 500） */
    public static <T> Result<T> failed(String message) {
        return new Result<>(ResultCode.FAILED.getCode(), message, null);
    }

    /** 失败，使用枚举中预定义的 code 与文案 */
    public static <T> Result<T> failed(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败，使用枚举的 code，但覆盖提示文案 */
    public static <T> Result<T> failed(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /**
     * 失败，使用任意 code 与自定义文案。
     *
     * <p>供 {@code GlobalExceptionHandler} 处理 {@link BusinessException} 时使用：
     * 异常自带的 code 决定 HTTP 状态码，message 决定返回给用户的文案。</p>
     */
    public static <T> Result<T> failed(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /** 参数校验失败（400），文案由 {@code @Valid} 校验注解或业务代码提供 */
    public static <T> Result<T> validateFailed(String message) {
        return new Result<>(ResultCode.VALIDATE_FAILED.getCode(), message, null);
    }

    /** 未登录或 token 过期（401） */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(ResultCode.UNAUTHORIZED.getCode(), message, null);
    }

    /** 无权限访问（403） */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(ResultCode.FORBIDDEN.getCode(), message, null);
    }
}