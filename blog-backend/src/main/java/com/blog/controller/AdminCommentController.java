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
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final CommentService commentService;

    @GetMapping("/page")
    public Result<PageResult<CommentVO>> getCommentPage(CommentQueryDTO queryDTO) {
        return Result.success(commentService.getCommentPage(queryDTO));
    }

    @GetMapping("/article/{articleId}")
    public Result<List<CommentVO>> getCommentsByArticleId(@PathVariable Long articleId) {
        return Result.success(commentService.getCommentsByArticleId(articleId));
    }

    @PostMapping("/{id}/reply")
    public Result<CommentVO> adminReplyComment(@PathVariable Long id, @Valid @RequestBody CommentDTO commentDTO) {
        return Result.success(commentService.adminReplyComment(id, commentDTO));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        commentService.deleteComment(id);
        return Result.success();
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDeleteComments(@RequestParam("data") List<Long> ids) {
        commentService.batchDeleteComments(ids);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateCommentStatus(@PathVariable Long id, @RequestParam Integer status) {
        commentService.updateCommentStatus(id, status);
        return Result.success();
    }
}