package com.blog.controller;

import com.blog.dto.LoginDTO;
import com.blog.dto.RegisterDTO;
import com.blog.interceptor.JwtAuthenticationInterceptor;
import com.blog.service.UserService;
import com.blog.utils.JwtUtil;
import com.blog.utils.RedisUtil;
import com.blog.vo.LoginVO;
import com.blog.vo.UserVO;
import com.blog.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        LoginVO loginVO = userService.login(loginDTO);
        return Result.success(loginVO);
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        UserVO userVO = userService.register(registerDTO);
        return Result.success(userVO);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader(jwtUtil.getTokenHead());
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(jwtUtil.getTokenHead() + " ")) {
            String token = authHeader.substring(jwtUtil.getTokenHead().length() + 1);
            // 先清除 ThreadLocal（如果同时经过拦截器）
            JwtAuthenticationInterceptor.clearCurrentUserId();
            // 真删 Redis 中 token，让服务端立即失效
            redisUtil.del("token:" + token);
        }
        return Result.success();
    }
}