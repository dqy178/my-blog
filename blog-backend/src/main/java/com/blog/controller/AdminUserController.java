package com.blog.controller;

import com.blog.entity.Article;
import com.blog.entity.Comment;
import com.blog.entity.User;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CommentMapper;
import com.blog.service.UserService;
import com.blog.vo.UserVO;
import com.blog.utils.RedisUtil;
import com.blog.common.Result;
import com.blog.common.ResultCode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final RedisUtil redisUtil;

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
        long articleCount = articleMapper.selectCount(new LambdaQueryWrapper<Article>().eq(Article::getDeleted, 0));
        vo.setArticleCount(articleCount);
        // 评论数：全站已审核通过的评论总数
        long commentCount = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, 1)
                .eq(Comment::getDeleted, 0));
        vo.setCommentCount(commentCount);
        // 总浏览量取全站未删除文章浏览量总和（DB + Redis 增量，与仪表盘保持一致）
        List<Article> allArticles = articleMapper.selectList(new LambdaQueryWrapper<Article>().eq(Article::getDeleted, 0));
        long totalViewCount = allArticles.stream()
                .mapToLong(a -> a.getViewCount() == null ? 0L : a.getViewCount())
                .sum();
        long redisViewCount = 0;
        if (!allArticles.isEmpty()) {
            List<String> viewKeys = allArticles.stream()
                    .map(a -> "article:view:" + a.getId())
                    .collect(Collectors.toList());
            List<Object> redisCounts = redisUtil.multiGet(viewKeys);
            if (redisCounts != null) {
                for (Object v : redisCounts) {
                    if (v != null) {
                        try {
                            redisViewCount += Long.parseLong(v.toString());
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
            }
        }
        vo.setViewCount(totalViewCount + redisViewCount);
        return Result.success(vo);
    }

    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UserVO userVO) {
        Long userId = com.blog.interceptor.JwtAuthenticationInterceptor.getCurrentUserId();
        if (userId == null) {
            return Result.failed(ResultCode.UNAUTHORIZED, "未登录");
        }
        return Result.success(userService.updateProfile(userId, userVO));
    }

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

    @PutMapping("/forget-password")
    public Result<Void> forgetPassword(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String answer1 = req.get("answer1");
        String answer2 = req.get("answer2");
        String newPassword = req.get("newPassword");
        userService.resetPasswordBySecurity(username, answer1, answer2, newPassword);
        return Result.success();
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
