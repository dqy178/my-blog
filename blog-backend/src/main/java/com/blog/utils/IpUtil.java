package com.blog.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 客户端 IP 工具。
 *
 * <p>服务在 nginx 之后运行，{@code request.getRemoteAddr()} 拿到的是反向代理的地址
 * （容器网段内的同一个 IP），直接用它做限流会误伤所有用户，
 * 因此优先取 nginx 透传的 X-Forwarded-For / X-Real-IP 头。</p>
 */
public final class IpUtil {

    /** 部分代理会用该占位符表示「IP 未知」，需要识别并跳过 */
    private static final String UNKNOWN = "unknown";

    private IpUtil() {
    }

    /**
     * 取当前请求的客户端 IP。
     *
     * <p>用于限流等场景。通过 {@code RequestContextHolder} 拿到当前线程的请求，
     * 取不到（如非 Web 线程调用）时返回 null，由调用方决定如何降级。</p>
     *
     * @return 客户端 IP，取不到时为 null
     */
    public static String currentIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        return resolveIp(servletAttrs.getRequest());
    }

    /**
     * 从请求中解析客户端 IP。
     *
     * <p>取值优先级：{@code X-Forwarded-For} 的第一个地址 → {@code X-Real-IP}
     * → {@code getRemoteAddr()}。</p>
     *
     * @param request 当前请求，可为 null
     * @return 解析出的 IP，全部来源都取不到时为 null
     */
    public static String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        // X-Forwarded-For 可能是 "client, proxy1, proxy2"，第一个才是真实客户端
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank() && !UNKNOWN.equalsIgnoreCase(xff)) {
            int comma = xff.indexOf(',');
            String first = comma > 0 ? xff.substring(0, comma) : xff;
            first = first.trim();
            if (!first.isEmpty() && !UNKNOWN.equalsIgnoreCase(first)) {
                return first;
            }
        }
        // X-Real-IP 是单值形式，取不到再用它
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !UNKNOWN.equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }
        // 最后兜底用直连地址。注意：服务在 nginx 之后时这里拿到的是代理 IP，
        // 直接拿去做限流会误伤所有用户，因此上面两个头必须优先。
        String remote = request.getRemoteAddr();
        return remote == null || UNKNOWN.equalsIgnoreCase(remote) ? null : remote;
    }
}
