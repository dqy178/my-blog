package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.ArticleDTO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.entity.Article;
import com.blog.entity.ArticleTag;
import com.blog.entity.Category;
import com.blog.entity.Tag;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.ArticleTagMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.mapper.TagMapper;
import com.blog.service.ArticleService;
import com.blog.service.CategoryService;
import com.blog.service.TagService;
import com.blog.utils.RedisUtil;
import com.blog.vo.ArticleVO;
import com.blog.vo.TagVO;
import com.blog.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final RedisUtil redisUtil;

    private static final String VIEW_COUNT_PREFIX = "article:view:";

    @Override
    public PageResult<ArticleVO> getArticlePage(ArticleQueryDTO queryDTO) {
        Page<Article> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getDeleted, 0);

        if (queryDTO.getCategoryId() != null) {
            wrapper.eq(Article::getCategoryId, queryDTO.getCategoryId());
        }
        if (queryDTO.getTagId() != null) {
            List<Long> articleIds = articleTagMapper.selectArticleIdsByTagId(queryDTO.getTagId());
            if (CollectionUtils.isEmpty(articleIds)) {
                return new PageResult<>(0L, List.of(), queryDTO.getPageNum(), queryDTO.getPageSize());
            }
            wrapper.in(Article::getId, articleIds);
        }
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            wrapper.like(Article::getTitle, queryDTO.getKeyword());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(Article::getStatus, queryDTO.getStatus());
        }
        // 后台管理场景下不强制过滤 status，便于管理草稿
        if (queryDTO.getIsTop() != null) {
            wrapper.eq(Article::getIsTop, queryDTO.getIsTop());
        }

        String sortField = StringUtils.hasText(queryDTO.getSortField()) ? queryDTO.getSortField() : "createTime";
        String sortOrder = StringUtils.hasText(queryDTO.getSortOrder()) ? queryDTO.getSortOrder() : "desc";
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        // 置顶文章优先
        wrapper.orderByDesc(Article::getIsTop);
        applyOrderBy(wrapper, sortField, isAsc);

        IPage<Article> articlePage = articleMapper.selectPage(page, wrapper);
        List<ArticleVO> records = convertToVOList(articlePage.getRecords());
        return new PageResult<>(articlePage.getTotal(), records, queryDTO.getPageNum(), queryDTO.getPageSize());
    }

    private void applyOrderBy(LambdaQueryWrapper<Article> wrapper, String sortField, boolean isAsc) {
        if (isAsc) {
            switch (sortField) {
                case "viewCount" -> wrapper.orderByAsc(Article::getViewCount);
                case "likeCount" -> wrapper.orderByAsc(Article::getLikeCount);
                case "commentCount" -> wrapper.orderByAsc(Article::getCommentCount);
                case "createTime" -> wrapper.orderByAsc(Article::getCreateTime);
                case "updateTime" -> wrapper.orderByAsc(Article::getUpdateTime);
                case "publishTime" -> wrapper.orderByAsc(Article::getPublishTime);
                case "isTop" -> wrapper.orderByAsc(Article::getIsTop);
                default -> wrapper.orderByAsc(Article::getCreateTime);
            }
        } else {
            switch (sortField) {
                case "viewCount" -> wrapper.orderByDesc(Article::getViewCount);
                case "likeCount" -> wrapper.orderByDesc(Article::getLikeCount);
                case "commentCount" -> wrapper.orderByDesc(Article::getCommentCount);
                case "createTime" -> wrapper.orderByDesc(Article::getCreateTime);
                case "updateTime" -> wrapper.orderByDesc(Article::getUpdateTime);
                case "publishTime" -> wrapper.orderByDesc(Article::getPublishTime);
                case "isTop" -> wrapper.orderByDesc(Article::getIsTop);
                default -> wrapper.orderByDesc(Article::getCreateTime);
            }
        }
    }

    @Override
    public ArticleVO getArticleDetail(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || article.getDeleted() == 1) {
            return null;
        }
        return convertToVO(article);
    }

    @Override
    public ArticleVO getPublishedArticleDetail(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || article.getDeleted() == 1 || article.getStatus() != 1) {
            return null;
        }
        ArticleVO vo = convertToVO(article);
        vo.setViewCount(getViewCount(id));
        return vo;
    }

    @Override
    @Transactional
    public ArticleVO createArticle(ArticleDTO articleDTO) {
        Article article = new Article();
        BeanUtils.copyProperties(articleDTO, article);
        article.setViewCount(0L);
        article.setLikeCount(0L);
        article.setCommentCount(0L);
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        if (articleDTO.getStatus() != null && articleDTO.getStatus() == 1) {
            article.setPublishTime(LocalDateTime.now());
        }

        articleMapper.insert(article);
        handleTags(article.getId(), articleDTO.getTagIds());
        // 只有真正发布的文章才计入分类数
        if (article.getStatus() != null && article.getStatus() == 1) {
            updateCategoryCount(articleDTO.getCategoryId(), 1);
        }
        return convertToVO(article);
    }

    @Override
    @Transactional
    public ArticleVO updateArticle(Long id, ArticleDTO articleDTO) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new com.blog.common.BusinessException(com.blog.common.ResultCode.NOT_FOUND, "文章不存在");
        }

        Long oldCategoryId = article.getCategoryId();
        BeanUtils.copyProperties(articleDTO, article);
        article.setId(id);
        article.setUpdateTime(LocalDateTime.now());
        if (articleDTO.getStatus() != null && articleDTO.getStatus() == 1) {
            article.setPublishTime(LocalDateTime.now());
        }

        articleMapper.updateById(article);
        handleTags(id, articleDTO.getTagIds());

        if (!oldCategoryId.equals(articleDTO.getCategoryId())) {
            updateCategoryCount(oldCategoryId, -1);
            updateCategoryCount(articleDTO.getCategoryId(), 1);
        }
        return convertToVO(article);
    }

    @Override
    @Transactional
    public void deleteArticle(Long id) {
        Article article = articleMapper.selectByIdIncludeDeleted(id);
        if (article == null) {
            return;
        }
        if (article.getDeleted() != null && article.getDeleted() == 1) {
            articleTagMapper.deleteByArticleId(id);
            return;
        }

        articleMapper.deleteById(id);
        articleTagMapper.deleteByArticleId(id);

        if (article.getStatus() != null && article.getStatus() == 1 && article.getCategoryId() != null) {
            updateCategoryCount(article.getCategoryId(), -1);
        }
    }

    @Override
    @Transactional
    public void batchDeleteArticles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            deleteArticle(id);
        }
    }

    @Override
    @Transactional
    public void updateArticleStatus(Long id, Integer status) {
        Article article = articleMapper.selectByIdIncludeDeleted(id);
        if (article == null || (article.getDeleted() != null && article.getDeleted() == 1)) {
            return;
        }

        Integer oldStatus = article.getStatus();
        article.setStatus(status);
        article.setUpdateTime(LocalDateTime.now());
        if (status != null && status == 1) {
            article.setPublishTime(LocalDateTime.now());
        }

        // 使用 LambdaUpdateWrapper 只更新需要的字段，避免 @TableLogic 下 updateById 覆盖 deleted 等字段
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Article> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.eq(Article::getId, id);
        uw.set(Article::getStatus, status);
        uw.set(Article::getUpdateTime, LocalDateTime.now());
        if (status != null && status == 1) {
            uw.set(Article::getPublishTime, LocalDateTime.now());
        }
        articleMapper.update(null, uw);

        if (oldStatus != null && status != null && !oldStatus.equals(status) && article.getCategoryId() != null) {
            if (Integer.valueOf(1).equals(oldStatus) && Integer.valueOf(0).equals(status)) {
                updateCategoryCount(article.getCategoryId(), -1);
            } else if (Integer.valueOf(0).equals(oldStatus) && Integer.valueOf(1).equals(status)) {
                updateCategoryCount(article.getCategoryId(), 1);
            }
        }
    }

    @Override
    public void batchUpdateArticleStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) return;
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Article> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.in(Article::getId, ids);
        uw.set(Article::getStatus, status);
        uw.set(Article::getUpdateTime, LocalDateTime.now());
        if (status != null && status == 1) {
            uw.set(Article::getPublishTime, LocalDateTime.now());
        }
        articleMapper.update(null, uw);
    }

    @Override
    public void updateArticleTop(Long id, Integer isTop) {
        Article article = articleMapper.selectByIdIncludeDeleted(id);
        if (article == null || (article.getDeleted() != null && article.getDeleted() == 1)) {
            return;
        }

        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Article> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.eq(Article::getId, id);
        uw.set(Article::getIsTop, isTop);
        uw.set(Article::getUpdateTime, LocalDateTime.now());
        articleMapper.update(null, uw);
    }

    @Override
    public void incrementViewCount(Long id) {
        try {
            String key = VIEW_COUNT_PREFIX + id;
            redisUtil.increment(key);
            // 按天记录浏览量，用于仪表盘趋势图
            String dailyKey = "view:daily:" + java.time.LocalDate.now().toString();
            redisUtil.increment(dailyKey);
        } catch (Exception e) {
            // Redis 不可用时忽略浏览量统计，避免影响页面访问
        }
    }

    public Long getViewCount(Long id) {
        try {
            String key = VIEW_COUNT_PREFIX + id;
            Object count = redisUtil.get(key);
            Long dbCount = articleMapper.selectById(id).getViewCount();
            if (dbCount == null) {
                dbCount = 0L;
            }
            if (count == null) {
                return dbCount;
            }
            return dbCount + Long.valueOf(count.toString());
        } catch (Exception e) {
            Article article = articleMapper.selectById(id);
            return article == null || article.getViewCount() == null ? 0L : article.getViewCount();
        }
    }

    @Override
    public List<ArticleVO> getHotArticles(Integer limit) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getDeleted, 0)
                .eq(Article::getStatus, 1);
        List<Article> articles = articleMapper.selectList(wrapper);
        // 合并 Redis 浏览量后排序
        List<ArticleVO> vos = new java.util.ArrayList<>(convertToVOList(articles));
        vos.sort((a, b) -> Long.compare(
                b.getViewCount() == null ? 0L : b.getViewCount(),
                a.getViewCount() == null ? 0L : a.getViewCount()));
        if (vos.size() > limit) {
            vos = new java.util.ArrayList<>(vos.subList(0, limit));
        }
        return vos;
    }

    @Override
    public List<ArticleVO> getLatestArticles(Integer limit) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getDeleted, 0)
                .eq(Article::getStatus, 1)
                .orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getCreateTime)
                .last("LIMIT " + limit);
        return convertToVOList(articleMapper.selectList(wrapper));
    }

    @Override
    public List<ArticleVO> getRelatedArticles(Long articleId, Long categoryId, Integer limit) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getDeleted, 0)
                .eq(Article::getStatus, 1)
                .eq(Article::getCategoryId, categoryId)
                .ne(Article::getId, articleId)
                .orderByDesc(Article::getCreateTime)
                .last("LIMIT " + limit);
        return convertToVOList(articleMapper.selectList(wrapper));
    }

    @Override
    public ArticleVO getPrevArticle(Long id) {
        // 在当前文章之前发布的最新一篇
        LambdaQueryWrapper<Article> w1 = new LambdaQueryWrapper<>();
        w1.eq(Article::getDeleted, 0).eq(Article::getStatus, 1)
                .lt(Article::getPublishTime, articleMapper.selectById(id) != null
                        ? articleMapper.selectById(id).getPublishTime() : java.time.LocalDateTime.now())
                .orderByDesc(Article::getPublishTime).last("LIMIT 1");
        Article prev = articleMapper.selectOne(w1);
        return prev == null ? null : convertToVO(prev);
    }

    @Override
    public ArticleVO getNextArticle(Long id) {
        Article cur = articleMapper.selectById(id);
        if (cur == null || cur.getPublishTime() == null) return null;
        LambdaQueryWrapper<Article> w2 = new LambdaQueryWrapper<>();
        w2.eq(Article::getDeleted, 0).eq(Article::getStatus, 1)
                .gt(Article::getPublishTime, cur.getPublishTime())
                .orderByAsc(Article::getPublishTime).last("LIMIT 1");
        Article next = articleMapper.selectOne(w2);
        return next == null ? null : convertToVO(next);
    }

    private void handleTags(Long articleId, List<Long> tagIds) {
        articleTagMapper.deleteByArticleId(articleId);
        if (!CollectionUtils.isEmpty(tagIds)) {
            List<ArticleTag> articleTags = tagIds.stream().map(tagId -> {
                ArticleTag at = new ArticleTag();
                at.setArticleId(articleId);
                at.setTagId(tagId);
                return at;
            }).toList();
            for (ArticleTag at : articleTags) {
                articleTagMapper.insert(at);
                tagService.updateArticleCount(at.getTagId(), 1);
            }
        }
    }

    private void updateCategoryCount(Long categoryId, Integer delta) {
        if (categoryId != null) {
            categoryService.updateArticleCount(categoryId, delta);
        }
    }

    private ArticleVO convertToVO(Article article) {
        ArticleVO vo = new ArticleVO();
        BeanUtils.copyProperties(article, vo);
        if (article.getCategoryId() != null) {
            Category category = categoryMapper.selectById(article.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }
        List<Long> tagIds = articleTagMapper.selectTagIdsByArticleId(article.getId());
        if (!CollectionUtils.isEmpty(tagIds)) {
            List<Tag> tags = tagMapper.selectBatchIds(tagIds);
            vo.setTagNames(tags.stream().map(Tag::getName).toList());
            vo.setTags(tags.stream().map(t -> {
                TagVO tagVO = new TagVO();
                BeanUtils.copyProperties(t, tagVO);
                return tagVO;
            }).toList());
        }
        vo.setViewCount(getViewCount(article.getId()));
        return vo;
    }

    /**
     * 批量构造 ArticleVO：一次性查所有分类、标签、Redis view counter，把 N+1 降到 3 次 SELECT。
     */
    private List<ArticleVO> convertToVOList(List<Article> articles) {
        if (CollectionUtils.isEmpty(articles)) {
            return new ArrayList<>();
        }
        List<Long> ids = articles.stream().map(Article::getId).toList();

        // 1) 分类
        Map<Long, Category> categoryMap = new LinkedHashMap<>();
        List<Long> categoryIds = articles.stream().map(Article::getCategoryId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (!categoryIds.isEmpty()) {
            categoryMapper.selectActiveByIds(categoryIds).forEach(c -> categoryMap.put(c.getId(), c));
        }

        // 2) 标签关系 + 标签实体
        Map<Long, List<Long>> tagsByArticle = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            articleTagMapper.selectTagIdsByArticleIds(ids).forEach(row -> {
                Long articleId = ((Number) row.get("article_id")).longValue();
                Long tagId = ((Number) row.get("tag_id")).longValue();
                tagsByArticle.computeIfAbsent(articleId, k -> new ArrayList<>()).add(tagId);
            });
        }
        Map<Long, Tag> tagMap = new LinkedHashMap<>();
        tagsByArticle.values().stream().flatMap(List::stream).distinct().forEach(tid -> {
            if (!tagMap.containsKey(tid)) tagMap.put(tid, tagMapper.selectById(tid));
        });

        // 3) 浏览量（Redis mget 一次）
        List<String> viewKeys = ids.stream().map(id -> VIEW_COUNT_PREFIX + id).toList();
        List<Object> viewValues = redisUtil.multiGet(viewKeys);
        Map<Long, Long> viewMap = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            Object v = viewValues.size() > i ? viewValues.get(i) : null;
            viewMap.put(ids.get(i), v == null ? 0L : Long.parseLong(String.valueOf(v)));
        }

        return articles.stream().map(article -> {
            ArticleVO vo = new ArticleVO();
            BeanUtils.copyProperties(article, vo);
            if (article.getCategoryId() != null) {
                Category c = categoryMap.get(article.getCategoryId());
                if (c != null) vo.setCategoryName(c.getName());
            }
            List<Long> tids = tagsByArticle.getOrDefault(article.getId(), Collections.emptyList());
            if (!tids.isEmpty()) {
                List<Tag> ts = tids.stream().map(tagMap::get).filter(java.util.Objects::nonNull).toList();
                vo.setTagNames(ts.stream().map(Tag::getName).toList());
                vo.setTags(ts.stream().map(t -> {
                    TagVO tagVO = new TagVO();
                    BeanUtils.copyProperties(t, tagVO);
                    return tagVO;
                }).toList());
            } else {
                // 没有标签时返回空数组而非 null，避免前端 .slice/.length 报错
                vo.setTagNames(new ArrayList<>());
                vo.setTags(new ArrayList<>());
            }
            long extra = viewMap.getOrDefault(article.getId(), 0L);
            vo.setViewCount((article.getViewCount() == null ? 0L : article.getViewCount()) + extra);
            return vo;
        }).toList();
    }
}