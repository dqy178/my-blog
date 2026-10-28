package com.blog.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.blog.vo.ArticleVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 文章服务的计数与发布状态逻辑测试。
 *
 * 覆盖历史上多次出错的几个点：
 * - 标签计数「只加不减」导致 tag.article_count 虚高
 * - 编辑文章时把 publish_time 重置为当前时间
 * - 下架草稿时 publish_time 未清空
 * - 浏览量只写 Redis 导致 ORDER BY view_count 排序失效
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceImplTest {

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
    private static final Long TAG_REDIS = 5L;

    private Article existing;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 把 lambda 表达式（Article::getPublishTime）解析成列名时
        // 依赖 MyBatis-Plus 的 TableInfo 缓存，单元测试没有 MyBatis 容器，
        // 需要手动初始化一次，否则 wrapper.getTargetSql() 拿不到真实列名。
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(builderAssistant, Article.class);
    }

    @BeforeEach
    void setUp() {
        existing = new Article();
        existing.setId(ARTICLE_ID);
        existing.setTitle("旧标题");
        existing.setCategoryId(CATEGORY_ID);
        existing.setStatus(1);
        existing.setDeleted(0);
        existing.setPublishTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        existing.setViewCount(0L);
        existing.setCommentCount(0L);
    }

    private ArticleDTO dto(Integer status, List<Long> tagIds) {
        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("新标题");
        dto.setSummary("摘要");
        dto.setContent("内容");
        dto.setCover("cover.png");
        dto.setCategoryId(CATEGORY_ID);
        dto.setStatus(status);
        dto.setIsTop(0);
        dto.setAllowComment(1);
        dto.setTagIds(tagIds);
        return dto;
    }

    private void givenArticle() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(existing);
    }

    // ------------------------------------------------------------------
    // 标签计数
    // ------------------------------------------------------------------

    @Test
    @DisplayName("编辑文章且标签不变时，不应重复累加标签计数")
    void updateArticle_sameTags_shouldNotChangeTagCount() {
        givenArticle();
        when(articleTagMapper.selectTagIdsByArticleId(ARTICLE_ID)).thenReturn(List.of(TAG_JAVA));

        articleService.updateArticle(ARTICLE_ID, dto(1, List.of(TAG_JAVA)));

        // 标签集合未变化 -> 不应有任何计数增减
        verify(tagService, never()).updateArticleCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("编辑文章更换标签时，旧标签 -1、新标签 +1")
    void updateArticle_changedTags_shouldDecrementOldAndIncrementNew() {
        givenArticle();
        when(articleTagMapper.selectTagIdsByArticleId(ARTICLE_ID)).thenReturn(List.of(TAG_JAVA));

        articleService.updateArticle(ARTICLE_ID, dto(1, List.of(TAG_REDIS)));

        verify(tagService).updateArticleCount(TAG_JAVA, -1);
        verify(tagService).updateArticleCount(TAG_REDIS, 1);
        verify(tagService, never()).updateArticleCount(TAG_JAVA, 1);
    }

    @Test
    @DisplayName("删除已发布文章时，其标签计数应 -1")
    void deleteArticle_published_shouldDecrementTagCount() {
        when(articleMapper.selectByIdIncludeDeleted(ARTICLE_ID)).thenReturn(existing);
        when(articleTagMapper.selectTagIdsByArticleId(ARTICLE_ID)).thenReturn(List.of(TAG_JAVA, TAG_REDIS));

        articleService.deleteArticle(ARTICLE_ID);

        verify(tagService).updateArticleCount(TAG_JAVA, -1);
        verify(tagService).updateArticleCount(TAG_REDIS, -1);
    }

    @Test
    @DisplayName("删除草稿文章时，标签计数不应变化（草稿不计入）")
    void deleteArticle_draft_shouldNotTouchTagCount() {
        existing.setStatus(0);
        when(articleMapper.selectByIdIncludeDeleted(ARTICLE_ID)).thenReturn(existing);

        articleService.deleteArticle(ARTICLE_ID);

        verify(tagService, never()).updateArticleCount(anyLong(), anyInt());
        verify(articleTagMapper).deleteByArticleId(ARTICLE_ID);
    }

    @Test
    @DisplayName("草稿转为发布时，标签与分类计数应 +1")
    void updateArticle_draftToPublished_shouldIncrementCounts() {
        existing.setStatus(0);
        existing.setPublishTime(null);
        givenArticle();
        when(articleTagMapper.selectTagIdsByArticleId(ARTICLE_ID)).thenReturn(Collections.emptyList());

        articleService.updateArticle(ARTICLE_ID, dto(1, List.of(TAG_JAVA)));

        verify(tagService).updateArticleCount(TAG_JAVA, 1);
        verify(categoryService).updateArticleCount(CATEGORY_ID, 1);
    }

    @Test
    @DisplayName("发布转为草稿时，标签与分类计数应 -1")
    void updateArticle_publishedToDraft_shouldDecrementCounts() {
        givenArticle();
        when(articleTagMapper.selectTagIdsByArticleId(ARTICLE_ID)).thenReturn(List.of(TAG_JAVA));

        articleService.updateArticle(ARTICLE_ID, dto(0, List.of(TAG_JAVA)));

        verify(tagService).updateArticleCount(TAG_JAVA, -1);
        verify(categoryService).updateArticleCount(CATEGORY_ID, -1);
    }

    // ------------------------------------------------------------------
    // 发布时间
    // ------------------------------------------------------------------

    @Test
    @DisplayName("编辑已发布文章时不应重置发布时间")
    void updateArticle_published_shouldKeepPublishTime() {
        givenArticle();
        when(articleTagMapper.selectTagIdsByArticleId(ARTICLE_ID)).thenReturn(Collections.emptyList());

        ArticleDTO dto = dto(1, Collections.emptyList());
        articleService.updateArticle(ARTICLE_ID, dto);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).updateById(captor.capture());
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), captor.getValue().getPublishTime(),
                "编辑已发布文章不应改变发布时间");
    }

    @Test
    @DisplayName("草稿转发布时应写入发布时间")
    void updateArticle_draftToPublished_shouldSetPublishTime() {
        existing.setStatus(0);
        existing.setPublishTime(null);
        givenArticle();
        when(articleTagMapper.selectTagIdsByArticleId(ARTICLE_ID)).thenReturn(Collections.emptyList());

        articleService.updateArticle(ARTICLE_ID, dto(1, Collections.emptyList()));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getPublishTime(), "首次发布应写入发布时间");
    }

    @Test
    @DisplayName("发布转草稿时应清空发布时间")
    void updateArticle_publishedToDraft_shouldClearPublishTime() {
        givenArticle();
        when(articleTagMapper.selectTagIdsByArticleId(ARTICLE_ID)).thenReturn(Collections.emptyList());

        articleService.updateArticle(ARTICLE_ID, dto(0, Collections.emptyList()));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).updateById(captor.capture());
        assertNull(captor.getValue().getPublishTime(), "下架后应清空发布时间");
    }

    // ------------------------------------------------------------------
    // 浏览量
    // ------------------------------------------------------------------

    @Test
    @DisplayName("浏览量应通过数据库原子自增写入")
    void incrementViewCount_shouldUseAtomicDbUpdate() {
        articleService.incrementViewCount(ARTICLE_ID);

        verify(articleMapper).incrementViewCount(ARTICLE_ID);
        verify(redisUtil, never()).increment(startsWith("article:view:"));
    }

    @Test
    @DisplayName("浏览量写入失败时不应抛异常（不能影响页面访问）")
    void incrementViewCount_dbFailure_shouldNotThrow() {
        when(articleMapper.incrementViewCount(ARTICLE_ID)).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> articleService.incrementViewCount(ARTICLE_ID));
    }

    // ------------------------------------------------------------------
    // 标签关联表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("更新文章时应先清空旧关联再写入新关联")
    void updateArticle_shouldRewriteTagRelations() {
        givenArticle();
        when(articleTagMapper.selectTagIdsByArticleId(ARTICLE_ID)).thenReturn(List.of(TAG_JAVA));

        articleService.updateArticle(ARTICLE_ID, dto(1, List.of(TAG_REDIS)));

        verify(articleTagMapper).deleteByArticleId(ARTICLE_ID);
        ArgumentCaptor<ArticleTag> captor = ArgumentCaptor.forClass(ArticleTag.class);
        verify(articleTagMapper, times(1)).insert(captor.capture());
        assertEquals(TAG_REDIS, captor.getValue().getTagId());
        assertEquals(ARTICLE_ID, captor.getValue().getArticleId());
    }

    @Test
    @DisplayName("标签列表为空时不应写入任何关联")
    void updateArticle_emptyTags_shouldNotInsert() {
        givenArticle();
        when(articleTagMapper.selectTagIdsByArticleId(ARTICLE_ID)).thenReturn(List.of(TAG_JAVA));

        articleService.updateArticle(ARTICLE_ID, dto(1, new ArrayList<>()));

        verify(articleTagMapper).deleteByArticleId(ARTICLE_ID);
        verify(articleTagMapper, never()).insert(any(ArticleTag.class));
        verify(tagService).updateArticleCount(TAG_JAVA, -1);
    }

    // ------------------------------------------------------------------
    // 上一篇 / 下一篇
    //
    // 前台列表按 publish_time 降序（最新的在最前），上下篇必须与列表顺序一致：
    // 上一篇 = 列表的前一个（更新），下一篇 = 列表的后一个（更旧）。
    // 方向搞反的话，最新的一篇会没有「下一篇」，而次新的一篇会被排到它前面。
    // ------------------------------------------------------------------

    private Article publishedArticle(Long id, LocalDateTime publishTime) {
        Article a = new Article();
        a.setId(id);
        a.setTitle("文章-" + id);
        a.setStatus(1);
        a.setDeleted(0);
        a.setPublishTime(publishTime);
        return a;
    }

    /** 捕获 articleMapper.selectOne 收到的条件，生成可读的 SQL。 */
    private String capturedSelectSql() {
        ArgumentCaptor<Wrapper<Article>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(articleMapper).selectOne(captor.capture());
        return captor.getValue().getTargetSql();
    }

    @Test
    @DisplayName("上一篇应取 publish_time 更大（列表中更新）的一篇")
    void getPrevArticle_shouldSelectNewerArticle() {
        givenArticle();
        when(articleMapper.selectOne(any(Wrapper.class)))
                .thenReturn(publishedArticle(101L, LocalDateTime.of(2026, 2, 1, 10, 0)));

        ArticleVO vo = articleService.getPrevArticle(ARTICLE_ID, null, null);

        assertNotNull(vo, "应返回上一篇");
        assertEquals(101L, vo.getId());
        String sql = capturedSelectSql();
        assertTrue(sql.contains("publish_time >"),
                "上一篇应查询 publish_time 大于当前文章的一篇，实际 SQL: " + sql);
        // 关键：在「更大」的那些里要取最小值（最接近当前的一篇），
        // 取最大值会一路跳回最新的一篇，看着像在几篇之间循环。
        assertTrue(sql.contains("ORDER BY publish_time ASC"),
                "上一篇应在更大的那些里取最小值，实际 SQL: " + sql);
    }

    @Test
    @DisplayName("下一篇应取 publish_time 更小（列表中更旧）的一篇")
    void getNextArticle_shouldSelectOlderArticle() {
        givenArticle();
        when(articleMapper.selectOne(any(Wrapper.class)))
                .thenReturn(publishedArticle(99L, LocalDateTime.of(2025, 12, 1, 10, 0)));

        ArticleVO vo = articleService.getNextArticle(ARTICLE_ID, null, null);

        assertNotNull(vo, "应返回下一篇");
        assertEquals(99L, vo.getId());
        String sql = capturedSelectSql();
        assertTrue(sql.contains("publish_time <"),
                "下一篇应查询 publish_time 小于当前文章的一篇，实际 SQL: " + sql);
        // 关键：在「更小」的那些里要取最大值（最接近当前的一篇），
        // 取最小值会一路跳到最旧的一篇。
        assertTrue(sql.contains("ORDER BY publish_time DESC"),
                "下一篇应在更小的那些里取最大值，实际 SQL: " + sql);
    }

    @Test
    @DisplayName("最新的一篇文章应没有「上一篇」，只有「下一篇」")
    void getPrevArticle_latestOne_shouldReturnNull() {
        givenArticle();
        when(articleMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertNull(articleService.getPrevArticle(ARTICLE_ID, null, null),
                "最新的一篇没有列表中更前的一篇，不应有「上一篇」");
    }

    @Test
    @DisplayName("最旧的一篇文章应没有「下一篇」，只有「上一篇」")
    void getNextArticle_oldestOne_shouldReturnNull() {
        givenArticle();
        when(articleMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertNull(articleService.getNextArticle(ARTICLE_ID, null, null),
                "最旧的一篇没有列表中更后的一篇，不应有「下一篇」");
    }

    @Test
    @DisplayName("未发布的文章不应返回上一篇和下一篇")
    void getPrevNextArticle_unpublished_shouldReturnNull() {
        existing.setStatus(0);
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(existing);

        assertNull(articleService.getPrevArticle(ARTICLE_ID, null, null));
        assertNull(articleService.getNextArticle(ARTICLE_ID, null, null));
        verify(articleMapper, never()).selectOne(any(Wrapper.class));
    }

    /**
     * 从标签页进入文章时，上下文必须限制在该标签的文章集合内。
     *
     * <p>否则用户在标签 X 里读文章，点了「下一篇」却跳到不相干标签的内容，浏览动线断了。</p>
     */
    @Test
    @DisplayName("传入 tagId 时上下篇应限定在该标签的文章集合内")
    void getPrevNextArticle_withTagId_shouldScopeToTag() {
        givenArticle();
        when(articleMapper.selectOne(any(Wrapper.class)))
                .thenReturn(publishedArticle(101L, LocalDateTime.of(2026, 2, 1, 10, 0)));

        articleService.getPrevArticle(ARTICLE_ID, 66L, null);

        String sql = capturedSelectSql();
        assertTrue(sql.contains("article_tag"),
                "限定标签时应走 article_tag 子查询，实际 SQL: " + sql);
        assertTrue(sql.contains("tag_id"),
                "子查询应带 tag_id 条件，实际 SQL: " + sql);
    }

    /**
     * 从分类页进入文章时，上下文必须限制在该分类的文章集合内。
     */
    @Test
    @DisplayName("传入 categoryId 时上下篇应限定在该分类的文章集合内")
    void getPrevNextArticle_withCategoryId_shouldScopeToCategory() {
        givenArticle();
        when(articleMapper.selectOne(any(Wrapper.class)))
                .thenReturn(publishedArticle(99L, LocalDateTime.of(2025, 12, 1, 10, 0)));

        articleService.getNextArticle(ARTICLE_ID, null, 33L);

        String sql = capturedSelectSql();
        assertTrue(sql.contains("category_id"),
                "限定分类时应带 category_id 条件，实际 SQL: " + sql);
    }

    /**
     * 不传作用域时保持全站范围（首页、直接输入 URL 进入的场景）。
     */
    @Test
    @DisplayName("不传作用域时上下篇为全站范围")
    void getPrevNextArticle_withoutScope_shouldNotAddScopeCondition() {
        givenArticle();
        when(articleMapper.selectOne(any(Wrapper.class)))
                .thenReturn(publishedArticle(101L, LocalDateTime.of(2026, 2, 1, 10, 0)));

        articleService.getPrevArticle(ARTICLE_ID, null, null);

        String sql = capturedSelectSql();
        assertFalse(sql.contains("article_tag"),
                "未限定作用域时不应出现标签子查询，实际 SQL: " + sql);
        assertFalse(sql.contains("category_id"),
                "未限定作用域时不应出现分类条件，实际 SQL: " + sql);
    }
}
