package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.CommentDTO;
import com.blog.dto.CommentQueryDTO;
import com.blog.entity.Article;
import com.blog.entity.User;
import com.blog.entity.Comment;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CommentMapper;
import com.blog.service.ArticleService;
import com.blog.service.UserService;
import com.blog.service.CommentService;
import com.blog.utils.RedisUtil;
import com.blog.vo.CommentVO;
import com.blog.common.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final ArticleService articleService;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    private static final String COMMENT_CACHE_PREFIX = "comment:article:";

    @Override
    public PageResult<CommentVO> getCommentPage(CommentQueryDTO queryDTO) {
        Page<Comment> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getDeleted, 0);

        if (queryDTO.getArticleId() != null) {
            wrapper.eq(Comment::getArticleId, queryDTO.getArticleId());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(Comment::getStatus, queryDTO.getStatus());
        }
        wrapper.orderByDesc(Comment::getCreateTime);

        IPage<Comment> commentPage = commentMapper.selectPage(page, wrapper);
        List<CommentVO> records = convertToVOList(commentPage.getRecords());
        return new PageResult<>(commentPage.getTotal(), records, queryDTO.getPageNum(), queryDTO.getPageSize());
    }

    @Override
    public List<CommentVO> getCommentsByArticleId(Long articleId) {
        // 用 JSON 字符串缓存，避免 List<Object> 跟 GenericJackson 反序列化映射歧义
        String cacheKey = COMMENT_CACHE_PREFIX + articleId;
        Object cacheRaw = redisUtil.get(cacheKey);
        if (cacheRaw instanceof String json && !json.isEmpty()) {
            try {
                return objectMapper.readValue(json, new TypeReference<List<CommentVO>>() {});
            } catch (Exception ignored) {
                redisUtil.del(cacheKey);
            }
        }

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getDeleted, 0)
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, 1)
                .isNull(Comment::getParentId)
                .orderByAsc(Comment::getCreateTime);

        List<Comment> comments = commentMapper.selectList(wrapper);
        List<CommentVO> result = buildCommentTree(comments);
        try {
            redisUtil.set(cacheKey, objectMapper.writeValueAsString(result), 10, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            // 缓存失败不影响业务
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("comment cache serialize failed: {}", e.getMessage());
        }
        return result;
    }

    @Override
    @Transactional
    public CommentVO createComment(CommentDTO commentDTO) {
        Article article = articleMapper.selectById(commentDTO.getArticleId());
        if (article == null || article.getDeleted() == 1 || article.getStatus() != 1) {
            throw new com.blog.common.BusinessException(com.blog.common.ResultCode.NOT_FOUND, "文章不存在");
        }
        if (article.getAllowComment() != 1) {
            throw new com.blog.common.BusinessException("该文章禁止评论");
        }

        Comment comment = new Comment();
        BeanUtils.copyProperties(commentDTO, comment);
        // 前台用户提交评论默认进入待审核；管理员回复才直接通过
        comment.setStatus(0);
        comment.setIsAdmin(0);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());

        // 管理员登录后发表评论，直接通过并标记为管理员
        Long currentUserId = com.blog.interceptor.JwtAuthenticationInterceptor.getCurrentUserId();
        if (currentUserId != null) {
            User currentUser = userService.getById(currentUserId);
            if (currentUser != null && "ROLE_ADMIN".equals(currentUser.getRole())) {
                comment.setIsAdmin(1);
                comment.setStatus(1);
                if (comment.getNickname() == null || comment.getNickname().isEmpty()) {
                    comment.setNickname(currentUser.getNickname());
                }
                if (comment.getEmail() == null || comment.getEmail().isEmpty()) {
                    comment.setEmail(currentUser.getEmail());
                }
            }
        }

        commentMapper.insert(comment);

        // 仅统计已通过的评论数
        LambdaQueryWrapper<Comment> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Comment::getArticleId, commentDTO.getArticleId())
                .eq(Comment::getDeleted, 0)
                .eq(Comment::getStatus, 1);
        article.setCommentCount(commentMapper.selectCount(countWrapper));
        articleMapper.updateById(article);

        redisUtil.del(COMMENT_CACHE_PREFIX + commentDTO.getArticleId());

        return convertToVO(comment);
    }

    @Override
    @Transactional
    public CommentVO adminReplyComment(Long id, CommentDTO commentDTO) {
        Comment parentComment = commentMapper.selectById(id);
        if (parentComment == null) {
            throw new com.blog.common.BusinessException(com.blog.common.ResultCode.NOT_FOUND, "父评论不存在");
        }

        Comment comment = new Comment();
        BeanUtils.copyProperties(commentDTO, comment);
        comment.setParentId(id);
        comment.setArticleId(parentComment.getArticleId());
        comment.setIsAdmin(1);
        comment.setNickname("管理员");
        comment.setStatus(1);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());

        commentMapper.insert(comment);

        Article article = articleMapper.selectById(parentComment.getArticleId());
        if (article != null) {
            LambdaQueryWrapper<Comment> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(Comment::getArticleId, parentComment.getArticleId())
                    .eq(Comment::getDeleted, 0)
                    .eq(Comment::getStatus, 1);
            article.setCommentCount(commentMapper.selectCount(countWrapper));
            articleMapper.updateById(article);
        }

        redisUtil.del(COMMENT_CACHE_PREFIX + parentComment.getArticleId());

        return convertToVO(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            return;
        }
        Long articleId = comment.getArticleId();

        // 先递归物理删除所有子评论
        deleteChildrenByParentId(id);

        // 物理删除当前评论
        commentMapper.deleteById(id);

        // 重新统计文章评论数（仅统计已通过的）
        Article article = articleMapper.selectById(articleId);
        if (article != null) {
            LambdaQueryWrapper<Comment> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(Comment::getArticleId, articleId)
                    .eq(Comment::getDeleted, 0)
                    .eq(Comment::getStatus, 1);
            article.setCommentCount(commentMapper.selectCount(countWrapper));
            articleMapper.updateById(article);
        }

        redisUtil.del(COMMENT_CACHE_PREFIX + articleId);
    }

    private void deleteChildrenByParentId(Long parentId) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getParentId, parentId);
        List<Comment> children = commentMapper.selectList(wrapper);
        for (Comment child : children) {
            deleteChildrenByParentId(child.getId());
            commentMapper.deleteById(child.getId());
        }
    }

    @Override
    @Transactional
    public void batchDeleteComments(List<Long> ids) {
        for (Long id : ids) {
            deleteComment(id);
        }
    }

    @Override
    public void updateCommentStatus(Long id, Integer status) {
        Comment comment = commentMapper.selectById(id);
        if (comment != null) {
            comment.setStatus(status);
            comment.setUpdateTime(LocalDateTime.now());
            commentMapper.updateById(comment);
            redisUtil.del(COMMENT_CACHE_PREFIX + comment.getArticleId());
        }
    }

    @Override
    public Long countByArticleId(Long articleId) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getArticleId, articleId)
                .eq(Comment::getDeleted, 0)
                .eq(Comment::getStatus, 1);
        return commentMapper.selectCount(wrapper);
    }

    private List<CommentVO> buildCommentTree(List<Comment> comments) {
        if (CollectionUtils.isEmpty(comments)) {
            return new ArrayList<>();
        }
        return comments.stream().map(this::convertToVOWithChildren).toList();
    }

    private CommentVO convertToVOWithChildren(Comment comment) {
        CommentVO vo = convertToVO(comment);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getDeleted, 0)
                .eq(Comment::getParentId, comment.getId())
                .eq(Comment::getStatus, 1)
                .orderByAsc(Comment::getCreateTime);
        List<Comment> children = commentMapper.selectList(wrapper);
        if (!CollectionUtils.isEmpty(children)) {
            vo.setChildren(children.stream().map(this::convertToVOWithChildren).toList());
        }
        return vo;
    }

    private CommentVO convertToVO(Comment comment) {
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(comment, vo);
        return vo;
    }

    private List<CommentVO> convertToVOList(List<Comment> comments) {
        if (CollectionUtils.isEmpty(comments)) {
            return new ArrayList<>();
        }
        return comments.stream().map(this::convertToVO).toList();
    }
}