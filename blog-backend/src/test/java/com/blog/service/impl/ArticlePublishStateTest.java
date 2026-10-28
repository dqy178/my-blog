package com.blog.service.impl;

import com.blog.dto.ArticleDTO;
import com.blog.entity.Article;
import com.blog.entity.ArticleTag;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.ArticleTagMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.mapper.TagMapper;
import com.blog.service.CategoryService;
import com.blog.service.TagService;
import com.blog.utils.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 文章发布状态与发布时间相关逻辑的回归测试。
 *
 * 覆盖几个曾经出错的点：
 * - 编辑文章时未传 status，曾导致 publish_time 被清空且分类/标签计数错误 -1
 * - 重复「发布」已发布文章，曾重置 publish_time（批量发布会把已发布文章一起带上）
 * - limit 参数未校验，?limit=-1 会导致 SQL 语法错误返回 500
 * - 客户端可指定文章主键 id
 */
@ExtendWith(MockitoExtension.class)
class ArticlePublishStateTest {

    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private ArticleTagMapper articleTagMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private CategoryService categoryService;
    @Mock
    private TagService tagService;
    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private ArticleServiceImpl articleService;

    private static final Long ARTICLE_ID = 100L;
    private static final Long CATEGORY_ID = 10L;
    private static final Long TAG_JAVA = 1L;

    private Article existing;

    @BeforeEach
    void setUp() {
        existing = new Article();
        existing.setId(ARTICLE_ID);
        existing.setTitle("标题");
        existing.setCategoryId(CATEGORY_ID);
        existing.setStatus(1);
        existing.setDeleted(0);
        existing.setPublishTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        existing.setViewCount(0L);
        existing.setCommentCount(0L);
    }

    private ArticleDTO dtoWithoutStatus() {
        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("新标题");
        dto.setSummary("摘要");
        dto.setContent("内容");
        dto.setCover("cover.png");
        dto.setCategoryId(CATEGORY_ID);
        dto.setIsTop(0);
        dto.setAllowComment(1);
        // 传与原文章相同的标签，验证「标签未变时计数不动」
        dto.setTagIds(new ArrayList<>(List.of(TAG_JAVA)));
        return dto;
    }

    // ------------------------------------------------------------------
    // status 未传
    // ------------------------------------------------------------------

    @Test
    @DisplayName("编辑文章未传 status 时应保持原状态，不清空 publish_time，也不改动计数")
    void updateArticle_withoutStatus_shouldKeepStatusAndPublishTime() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(existing);
        when(articleTagMapper.selectTagIdsByArticleId(ARTICLE_ID)).thenReturn(List.of(TAG_JAVA));

        articleService.updateArticle(ARTICLE_ID, dtoWithoutStatus());

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).updateById(captor.capture());
        Article saved = captor.getValue();

        assertEquals(1, saved.getStatus(), "未传 status 时应保持原状态");
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), saved.getPublishTime(),
                "未传 status 时不应清空 publish_time");
        verify(tagService, never()).updateArticleCount(anyLong(), anyInt());
        verify(categoryService, never()).updateArticleCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("编辑文章未传 tagIds 时不应清空已有标签关联")
    void updateArticle_withoutTagIds_shouldNotClearTagRelations() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(existing);
        when(articleTagMapper.selectTagIdsByArticleId(ARTICLE_ID)).thenReturn(List.of(TAG_JAVA));

        ArticleDTO dto = dtoWithoutStatus();
        dto.setTagIds(null);
        articleService.updateArticle(ARTICLE_ID, dto);

        // 标签集合未变 -> 不应有任何计数增减
        verify(tagService, never()).updateArticleCount(anyLong(), anyInt());
    }
    // ------------------------------------------------------------------
    // 重复发布
    // ------------------------------------------------------------------
    @Test
    @DisplayName("对已发布文章再次发布时不应重置 publish_time")
    void resolvePublishTime_republish_shouldKeepPublishTime() {
        LocalDateTime original = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime result = ArticleServiceImpl.resolvePublishTime(original, 1, 1);
        assertEquals(original, result, "重复发布不应重置 publish_time");
    }

    @Test
    @DisplayName("草稿首次发布时才写入 publish_time")
    void resolvePublishTime_firstPublish_shouldSetPublishTime() {
        LocalDateTime result = ArticleServiceImpl.resolvePublishTime(null, 0, 1);
        assertNotNull(result, "首次发布应写入 publish_time");
    }

    @Test
    @DisplayName("发布转草稿时应清空 publish_time")
    void resolvePublishTime_unpublish_shouldClearPublishTime() {
        LocalDateTime original = LocalDateTime.of(2026, 1, 1, 10, 0);
        assertNull(ArticleServiceImpl.resolvePublishTime(original, 1, 0), "下架应清空 publish_time");
    }

    @Test
    @DisplayName("非法 status 应被拒绝")
    void updateArticleStatus_invalidStatus_shouldThrow() {
        when(articleMapper.selectByIdIncludeDeleted(ARTICLE_ID)).thenReturn(existing);

        assertThrows(com.blog.common.BusinessException.class,
                () -> articleService.updateArticleStatus(ARTICLE_ID, 5));
        verify(articleMapper, never()).update(any(), any());
    }

    // ------------------------------------------------------------------
    // limit 校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("limit 为负数时应回落到默认值")
    void normalizeLimit_negative_shouldFallBackToDefault() {
        assertEquals(10, ArticleServiceImpl.normalizeLimit(-1, 10, 50));
        assertEquals(10, ArticleServiceImpl.normalizeLimit(0, 10, 50));
        assertEquals(10, ArticleServiceImpl.normalizeLimit(null, 10, 50));
    }

    @Test
    @DisplayName("limit 超过上限时应被夹取")
    void normalizeLimit_tooLarge_shouldBeClamped() {
        assertEquals(50, ArticleServiceImpl.normalizeLimit(99999, 10, 50));
        assertEquals(20, ArticleServiceImpl.normalizeLimit(100, 5, 20));
    }

    @Test
    @DisplayName("合法 limit 应原样保留")
    void normalizeLimit_valid_shouldBeKept() {
        assertEquals(7, ArticleServiceImpl.normalizeLimit(7, 10, 50));
    }

    // ------------------------------------------------------------------
    // 主键
    // ------------------------------------------------------------------

    @Test
    @DisplayName("创建文章时不应接受客户端指定的主键 id")
    void createArticle_shouldNotAcceptClientProvidedId() {
        ArticleDTO dto = dtoWithoutStatus();
        dto.setId(999L);
        dto.setStatus(0);

        articleService.createArticle(dto);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).insert(captor.capture());
        assertNull(captor.getValue().getId(), "主键应由数据库生成，不能由客户端指定");
    }
}
