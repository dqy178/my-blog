package com.blog.controller;

import com.blog.dto.LoginDTO;
import com.blog.interceptor.JwtAuthenticationInterceptor;
import com.blog.service.UserService;
import com.blog.utils.CaptchaUtil;
import com.blog.utils.JwtUtil;
import com.blog.utils.RedisUtil;
import com.blog.vo.CaptchaVO;
import com.blog.vo.LoginVO;
import com.blog.vo.UserVO;
import com.blog.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口。
 *
 * <p>全部为公开访问：登录、获取验证码本身就需要未登录状态；
 * 登出接口虽然公开，但会尝试从 Authorization 头解析 token 并删除其在 Redis 中的记录。</p>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final CaptchaUtil captchaUtil;

    /**
     * 登录。
     *
     * @param loginDTO 用户名、密码与「记住我」标记
     * @return 令牌与用户信息
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        LoginVO loginVO = userService.login(loginDTO);
        return Result.success(loginVO);
    }

    /**
     * 获取算术验证码，供游客发表评论时使用。
     *
     * <p>个人博客不开放注册，验证码只用于阻挡脚本批量灌水。</p>
     */
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.success(captchaUtil.generate());
    }

    /**
     * 登出：删除 Redis 中的 token，使服务端立即失效该会话。
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        // 客户端发的是 "Authorization: Bearer <token>"，
        // 不能用 getHeader(tokenHead) 取（那会去找一个叫 "Bearer" 的头，永远取不到），
        // 否则 Redis 里的 token 不会被删除，登出后 token 仍然有效直到自然过期。
        String authHeader = request.getHeader("Authorization");
        String head = jwtUtil.getTokenHead();
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(head + " ")) {
            String token = authHeader.substring(head.length() + 1).trim();
            if (StringUtils.hasText(token)) {
                // 先清除 ThreadLocal（如果同时经过拦截器）
                JwtAuthenticationInterceptor.clearCurrentUserId();
                // 真删 Redis 中 token，让服务端立即失效
                redisUtil.del("token:" + token);
                // 同时从「用户 -> token 集合」索引里移除，避免索引无限增长
                Long userId = jwtUtil.getUserIdFromToken(token);
                if (userId != null) {
                    redisUtil.setRemove("user:tokens:" + userId, token);
                }
            }
        }
        return Result.success();
    }
}