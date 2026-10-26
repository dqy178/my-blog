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

@RestController
@RequestMapping("/front/comments")
@RequiredArgsConstructor
public class FrontCommentController {

    private final CommentService commentService;

    @GetMapping("/article/{articleId}")
    public Result<List<CommentVO>> getCommentsByArticleId(@PathVariable Long articleId) {
        return Result.success(commentService.getCommentsByArticleId(articleId));
    }

    @GetMapping("/page")
    public Result<PageResult<CommentVO>> getCommentPage(CommentQueryDTO queryDTO) {
        queryDTO.setStatus(1);
        return Result.success(commentService.getCommentPage(queryDTO));
    }

    @PostMapping
    public Result<CommentVO> createComment(@Valid @RequestBody CommentDTO commentDTO) {
        CommentVO comment = commentService.createComment(commentDTO);
        return Result.success(comment);
    }
}