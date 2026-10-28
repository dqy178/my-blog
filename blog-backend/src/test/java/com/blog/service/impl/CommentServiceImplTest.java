package com.blog.service.impl;

import com.blog.common.BusinessException;
import com.blog.common.ResultCode;
import com.blog.dto.CommentDTO;
import com.blog.entity.Article;
import com.blog.entity.Comment;
import com.blog.entity.User;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CommentMapper;
import com.blog.service.ArticleService;
import com.blog.service.UserService;
import com.blog.utils.CaptchaUtil;
import com.blog.utils.HtmlSanitizer;
import com.blog.utils.RedisUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 评论服务的审核与计数逻辑测试。
 *
 * 覆盖：审核状态变更后必须重算文章的已通过评论数（历史上审核通过/拒绝都不重算），
 * 以及游客评论与管理员评论的标识差异。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentMapper commentMapper;
    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private UserService userService;
    @Mock
    private ArticleService articleService;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private CaptchaUtil captchaUtil;
    @Mock
    private HtmlSanitizer htmlSanitizer;

    @InjectMocks
    private CommentServiceImpl commentService;

    private static final Long ARTICLE_ID = 100L;
    private static final Long COMMENT_ID = 500L;

    private Comment comment;
    private Article article;

    @BeforeEach
    void setUp() {
        comment = new Comment();
        comment.setId(COMMENT_ID);
        comment.setArticleId(ARTICLE_ID);
        comment.setParentId(null);
        comment.setNickname("游客");
        comment.setEmail("guest@test.com");
        comment.setContent("内容");
        comment.setStatus(0);
        comment.setIsAdmin(0);
        comment.setDeleted(0);

        article = new Article();
        article.setId(ARTICLE_ID);
        article.setCommentCount(0L);
        article.setDeleted(0);
        article.setStatus(1);
        article.setAllowComment(1);
    }

    @Test
    @DisplayName("审核通过评论后应重算文章评论数")
    void updateCommentStatus_approve_shouldRefreshArticleCommentCount() {
        when(commentMapper.selectById(COMMENT_ID)).thenReturn(comment);
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article);
        when(commentMapper.selectCount(any())).thenReturn(1L);

        commentService.updateCommentStatus(COMMENT_ID, 1);

        // 评论状态应更新为已通过
        assertEquals(1, comment.getStatus());
        // 文章评论数应被重算并保存
        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getCommentCount());
    }

    @Test
    @DisplayName("审核拒绝评论后应重算文章评论数（已通过数减少）")
    void updateCommentStatus_reject_shouldRefreshArticleCommentCount() {
        when(commentMapper.selectById(COMMENT_ID)).thenReturn(comment);
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article);
        when(commentMapper.selectCount(any())).thenReturn(0L);

        commentService.updateCommentStatus(COMMENT_ID, 2);

        assertEquals(2, comment.getStatus());
        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).updateById(captor.capture());
        assertEquals(0L, captor.getValue().getCommentCount());
    }

    @Test
    @DisplayName("审核状态变更后应清理评论缓存")
    void updateCommentStatus_shouldClearCommentCache() {
        when(commentMapper.selectById(COMMENT_ID)).thenReturn(comment);
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article);
        when(commentMapper.selectCount(any())).thenReturn(0L);

        commentService.updateCommentStatus(COMMENT_ID, 1);

        verify(redisUtil).del("comment:article:" + ARTICLE_ID);
    }

    @Test
    @DisplayName("评论不存在时不应做任何修改")
    void updateCommentStatus_notFound_shouldDoNothing() {
        when(commentMapper.selectById(COMMENT_ID)).thenReturn(null);

        commentService.updateCommentStatus(COMMENT_ID, 1);

        verify(articleMapper, never()).updateById(any(Article.class));
        verify(redisUtil, never()).del(any(String.class));
    }

    @Test
    @DisplayName("游客提交评论时应标记为非管理员且待审核")
    void createComment_guest_shouldBeNonAdminAndPending() {
        CommentDTO dto = new CommentDTO();
        dto.setArticleId(ARTICLE_ID);
        dto.setContent("游客评论");
        dto.setNickname("游客");
        dto.setEmail("guest@test.com");
        dto.setCaptchaId("captcha-id");
        dto.setCaptcha("8");

        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article);
        when(commentMapper.selectCount(any())).thenReturn(0L);
        // 游客评论需要通过验证码校验
        doNothing().when(captchaUtil).verify(anyString(), anyString());
        // 净化器放行（真实实现由独立测试覆盖）
        when(htmlSanitizer.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));

        commentService.createComment(dto);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).insert(captor.capture());
        Comment saved = captor.getValue();
        assertEquals(0, saved.getIsAdmin(), "游客评论不应标记为管理员");
        assertEquals(0, saved.getStatus(), "游客评论应待审核");
    }

    @Test
    @DisplayName("游客评论缺少验证码时应被拒绝")
    void createComment_guestWithoutCaptcha_shouldThrow() {
        CommentDTO dto = new CommentDTO();
        dto.setArticleId(ARTICLE_ID);
        dto.setContent("游客评论");
        dto.setNickname("游客");
        dto.setEmail("guest@test.com");

        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(article);
        // verify 内部对空 captchaId/captcha 抛 BusinessException
        doThrow(new BusinessException(ResultCode.VALIDATE_FAILED, "请输入验证码"))
                .when(captchaUtil).verify(any(), any());

        assertThrows(BusinessException.class, () -> commentService.createComment(dto));
        verify(commentMapper, never()).insert(any(Comment.class));
    }
}
