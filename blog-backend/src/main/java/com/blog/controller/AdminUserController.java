package com.blog.controller;

import com.blog.entity.Article;
import com.blog.entity.Comment;
import com.blog.entity.User;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CommentMapper;
import com.blog.service.UserService;
import com.blog.vo.UserVO;
import com.blog.common.Result;
import com.blog.common.ResultCode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 后台个人中心接口。
 *
 * <p>除「密保重置密码」外，其余接口都要求登录（{@code /admin/**} 强制 ROLE_ADMIN）。
 * 当前登录用户通过 JWT 拦截器写入的 ThreadLocal 获取，不从请求体读取 id，
 * 避免越权操作他人账号。</p>
 */
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;

    /**
     * 获取当前登录用户的资料与博客统计。
     */
    @GetMapping("/profile")
    public Result<UserVO> getProfile() {
        Long userId = com.blog.interceptor.JwtAuthenticationInterceptor.getCurrentUserId();
        if (userId == null) {
            return Result.failed(ResultCode.UNAUTHORIZED, "未登录");
        }
        User user = userService.getById(userId);
        if (user == null) {
            return Result.failed(ResultCode.NOT_FOUND, "用户不存在");
        }
        UserVO vo = toVO(user);
        // 个人博客单用户：文章数取全站未删除文章数
        long articleCount = articleMapper.selectCount(new LambdaQueryWrapper<Article>().eq(Article::getDeleted, 0).eq(Article::getStatus, 1));
        vo.setArticleCount(articleCount);
        // 评论数：全站已审核通过的评论总数
        long commentCount = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, 1)
                .eq(Comment::getDeleted, 0));
        vo.setCommentCount(commentCount);
        // 总浏览量取全站未删除文章浏览量总和（浏览量已存库，与仪表盘保持一致）
        List<Article> allArticles = articleMapper.selectList(new LambdaQueryWrapper<Article>().eq(Article::getDeleted, 0));
        long totalViewCount = allArticles.stream()
                .mapToLong(a -> a.getViewCount() == null ? 0L : a.getViewCount())
                .sum();
        vo.setViewCount(totalViewCount);
        return Result.success(vo);
    }

    /**
     * 更新当前登录用户的资料。
     *
     * <p>只接受白名单字段（昵称、邮箱、头像、简介、座右铭），
     * password / role / status 会被服务端忽略。</p>
     */
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UserVO userVO) {
        Long userId = com.blog.interceptor.JwtAuthenticationInterceptor.getCurrentUserId();
        if (userId == null) {
            return Result.failed(ResultCode.UNAUTHORIZED, "未登录");
        }
        return Result.success(userService.updateProfile(userId, userVO));
    }

    /**
     * 修改密码。修改成功后该用户的所有会话立即失效。
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestBody Map<String, String> req) {
        Long userId = com.blog.interceptor.JwtAuthenticationInterceptor.getCurrentUserId();
        if (userId == null) {
            return Result.failed(ResultCode.UNAUTHORIZED, "未登录");
        }
        String oldPassword = req.get("oldPassword");
        String newPassword = req.get("newPassword");
        userService.updatePassword(userId, oldPassword, newPassword);
        return Result.success();
    }

    /**
     * 修改密保答案。修改成功后该用户的所有会话立即失效。
     */
    @PutMapping("/security-answers")
    public Result<Void> updateSecurityAnswers(@RequestBody Map<String, String> req) {
        Long userId = com.blog.interceptor.JwtAuthenticationInterceptor.getCurrentUserId();
        if (userId == null) {
            return Result.failed(ResultCode.UNAUTHORIZED, "未登录");
        }
        String answer1 = req.get("answer1");
        String answer2 = req.get("answer2");
        userService.updateSecurityAnswers(userId, answer1, answer2);
        return Result.success();
    }

    /**
     * 通过密保答案重置密码。
     *
     * <p>该接口是公开的（已加入鉴权白名单），因为用户此时无法登录。
     * 服务端通过密保答案与限流来防止被暴力猜解。</p>
     */
    @PutMapping("/forget-password")
    public Result<Void> forgetPassword(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String answer1 = req.get("answer1");
        String answer2 = req.get("answer2");
        String newPassword = req.get("newPassword");
        userService.resetPasswordBySecurity(username, answer1, answer2, newPassword);
        return Result.success();
    }

    /**
     * 装配用户 VO。
     */
    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
