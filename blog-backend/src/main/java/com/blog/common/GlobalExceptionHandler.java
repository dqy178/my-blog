package com.blog.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>把各类异常统一转换为 {@link Result} 结构，保证前端永远收到
 * {@code {code, message, data}} 形态的 JSON，而不会遇到 HTML 错误页或空响应体。</p>
 *
 * <p>处理原则：</p>
 * <ul>
 *   <li>客户端错误（400/404/409/415）返回具体原因，便于前端直接提示用户</li>
 *   <li>业务异常按其自带 code 映射 HTTP 状态码</li>
 *   <li>未预期的异常统一返回 500 + 通用文案，<b>不向前端暴露堆栈或 SQL 细节</b>，
 *       完整异常在服务端用 log.error 记录</li>
 * </ul>
 *
 * <p>HTTP 状态码与响应体中的 {@code code} 保持一致，前端拦截器可按 status 分支处理
 * （401 跳登录、403 提示无权限、409 提示冲突等）。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 {@code @Valid} 校验失败（RequestBody 上的 DTO）。
     *
     * <p>把多个字段的校验消息用「; 」拼接，前端可直接整体展示。</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.validateFailed(message);
    }

    /**
     * 处理表单/查询参数绑定失败。
     *
     * <p>与上面的区别：绑定异常来自非 RequestBody 的参数对象（如 {@code @ModelAttribute}）。</p>
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return Result.validateFailed(message);
    }

    /**
     * 处理方法级参数约束校验失败（如 {@code @Validated} 类上的 {@code @NotBlank}）。
     *
     * <p>与 DTO 上的 {@code @NotBlank} 区分：这里的约束直接标在控制器方法参数上。</p>
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", message);
        return Result.validateFailed(message);
    }

    /**
     * 处理唯一索引冲突（如分类名、标签名、用户名重复）。
     *
     * <p>返回统一的友好文案，不把数据库原始错误信息暴露给前端。</p>
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<String> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("数据重复: {}", e.getMessage());
        return Result.failed("数据已存在，请勿重复添加");
    }

    /**
     * 请求参数类型不匹配，例如批量删除接口 ?data=abc 无法转换为 Long。
     * 属于客户端错误，应返回 400 而非 500。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getName());
        return Result.validateFailed("参数类型不正确: " + e.getName());
    }

    /**
     * 缺少必需的请求参数，例如调用批量删除接口时未传 data。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleMissingParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少必要参数: {}", e.getParameterName());
        return Result.validateFailed("缺少必要参数: " + e.getParameterName());
    }

    /**
     * 处理业务异常。
     *
     * <p>HTTP 状态码由异常自带的 code 决定：409/404/401/403/429 分别映射为
     * 对应的状态，其余一律按 400 处理。之所以不走 {@code @ResponseStatus} 注解，
     * 是因为状态码需要在运行时根据 code 动态推导。</p>
     */
    @ExceptionHandler(BusinessException.class)
    public org.springframework.http.ResponseEntity<Result<String>> handleBusinessException(BusinessException e) {
        // 通过 ResultCode 的枚举 code 推断 HTTP 状态
        log.warn("业务异常: {}", e.getMessage());
        org.springframework.http.HttpStatus status = org.springframework.http.HttpStatus.BAD_REQUEST;
        if (e.getCode() != null) {
            if (e.getCode() == 409) status = org.springframework.http.HttpStatus.CONFLICT;
            else if (e.getCode() == 404) status = org.springframework.http.HttpStatus.NOT_FOUND;
            else if (e.getCode() == 401) status = org.springframework.http.HttpStatus.UNAUTHORIZED;
            else if (e.getCode() == 403) status = org.springframework.http.HttpStatus.FORBIDDEN;
            else if (e.getCode() == 429) status = org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
        }
        return org.springframework.http.ResponseEntity
                .status(status)
                .body(Result.failed(e.getCode(), e.getMessage()));
    }

    /**
     * 处理 Content-Type 不受支持（如用 application/json 提交 form-data）。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<String> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的 Content-Type: {}", e.getContentType());
        return Result.failed(ResultCode.VALIDATE_FAILED.getCode(), "不支持的请求内容类型");
    }

    /**
     * 处理请求体解析失败（JSON 格式非法、字段类型不匹配等）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleMessageNotReadable(HttpMessageNotReadableException e) {
        // 请求体格式非法（JSON 解析失败等）属于客户端错误
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.failed(ResultCode.VALIDATE_FAILED.getCode(), "请求参数格式错误");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<String> handleNoResourceFoundException(NoResourceFoundException e) {
        // 路径不存在属于 404，不应落到兜底的 500
        log.warn("资源不存在: {}", e.getResourcePath());
        return Result.failed(ResultCode.NOT_FOUND.getCode(), "资源不存在");
    }

    /**
     * 兜底分支：处理所有未被上面捕获的异常。
     *
     * <p>记录完整堆栈便于排查，但只向客户端返回「系统内部错误，请联系管理员」，
     * 避免把内部实现（类名、SQL、文件路径）泄露给前端。</p>
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {} - {}", request.getRequestURI(), e.getMessage(), e);
        return Result.failed("系统内部错误，请联系管理员");
    }
}