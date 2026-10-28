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
 * 后台评论管理接口。
 *
 * <p>路径前缀 {@code /admin/**} 强制要求 {@code ROLE_ADMIN} 角色。</p>
 */
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final CommentService commentService;

    /**
     * 分页查询评论（可按审核状态筛选）。
     */
    @GetMapping("/page")
    public Result<PageResult<CommentVO>> getCommentPage(CommentQueryDTO queryDTO) {
        return Result.success(commentService.getCommentPage(queryDTO));
    }

    /**
     * 获取文章下的评论树。
     */
    @GetMapping("/article/{articleId}")
    public Result<List<CommentVO>> getCommentsByArticleId(@PathVariable Long articleId) {
        return Result.success(commentService.getCommentsByArticleId(articleId));
    }

    /**
     * 管理员回复评论（挂到指定评论下，直接通过审核）。
     */
    @PostMapping("/{id}/reply")
    public Result<CommentVO> adminReplyComment(@PathVariable Long id, @Valid @RequestBody CommentDTO commentDTO) {
        return Result.success(commentService.adminReplyComment(id, commentDTO));
    }

    /**
     * 删除评论（级联删除其子回复）。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        commentService.deleteComment(id);
        return Result.success();
    }

    /**
     * 批量删除评论。
     *
     * @param ids 评论 ID 列表，逗号分隔（如 {@code ?data=1,2,3}）
     */
    @DeleteMapping("/batch")
    public Result<Void> batchDeleteComments(@RequestParam("data") List<Long> ids) {
        commentService.batchDeleteComments(ids);
        return Result.success();
    }

    /**
     * 修改评论审核状态：1=通过，2=拒绝。
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateCommentStatus(@PathVariable Long id, @RequestParam Integer status) {
        commentService.updateCommentStatus(id, status);
        return Result.success();
    }
}