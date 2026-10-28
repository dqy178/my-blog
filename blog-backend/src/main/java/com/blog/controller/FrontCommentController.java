package com.blog.controller;

import com.blog.dto.CommentDTO;
import com.blog.dto.CommentQueryDTO;
import com.blog.service.CommentService;
import com.blog.vo.CommentVO;
import com.blog.common.Result;
import com.blog.common.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台评论接口。
 *
 * <p>查询接口公开；提交评论接口为「可选鉴权」——游客可以提交（需验证码 + IP 限流），
 * 登录的管理员提交则直接通过审核并标记为博主。</p>
 */
@RestController
@RequestMapping("/front/comments")
@RequiredArgsConstructor
public class FrontCommentController {

    private final CommentService commentService;

    /**
     * 获取文章下的评论树（仅已通过的评论）。
     */
    @GetMapping("/article/{articleId}")
    public Result<List<CommentVO>> getCommentsByArticleId(@PathVariable Long articleId) {
        return Result.success(commentService.getCommentsByArticleId(articleId));
    }

    /**
     * 分页查询评论（强制只返回已通过的）。
     */
    @GetMapping("/page")
    public Result<PageResult<CommentVO>> getCommentPage(CommentQueryDTO queryDTO) {
        queryDTO.setStatus(1);
        return Result.success(commentService.getCommentPage(queryDTO));
    }

    /**
     * 提交评论。
     *
     * <p>游客必须携带 {@code captchaId} 与 {@code captcha}，否则返回参数校验失败。</p>
     */
    @PostMapping
    public Result<CommentVO> createComment(@Valid @RequestBody CommentDTO commentDTO) {
        CommentVO comment = commentService.createComment(commentDTO);
        return Result.success(comment);
    }
}