package com.blog.vo;

import lombok.Data;

/**
 * 登录成功返回对象。
 */
@Data
public class LoginVO {

    /** JWT 令牌。前端存入 sessionStorage，后续请求通过 Authorization 头携带 */
    private String token;

    /** 当前登录用户信息 */
    private UserVO user;
}