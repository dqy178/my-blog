package com.blog.service;

import com.blog.dto.CommentDTO;
import com.blog.dto.CommentQueryDTO;
import com.blog.vo.CommentVO;
import com.blog.common.PageResult;

import java.util.List;

public interface CommentService {

    PageResult<CommentVO> getCommentPage(CommentQueryDTO queryDTO);

    List<CommentVO> getCommentsByArticleId(Long articleId);

    CommentVO createComment(CommentDTO commentDTO);

    CommentVO adminReplyComment(Long id, CommentDTO commentDTO);

    void deleteComment(Long id);

    void batchDeleteComments(List<Long> ids);

    void updateCommentStatus(Long id, Integer status);

    Long countByArticleId(Long articleId);
}