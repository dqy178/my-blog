package com.blog.service;
import java.util.List;

import com.blog.dto.ArticleDTO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.vo.ArticleVO;
import com.blog.common.PageResult;

import java.util.List;

/**
 * 文章服务接口。
 */
public interface ArticleService {

    PageResult<ArticleVO> getArticlePage(ArticleQueryDTO queryDTO);

    ArticleVO getArticleDetail(Long id);

    ArticleVO getPublishedArticleDetail(Long id);

    ArticleVO createArticle(ArticleDTO articleDTO);

    ArticleVO updateArticle(Long id, ArticleDTO articleDTO);

    void deleteArticle(Long id);

    void batchDeleteArticles(List<Long> ids);

    void updateArticleStatus(Long id, Integer status);

    void batchUpdateArticleStatus(List<Long> ids, Integer status);

    void updateArticleTop(Long id, Integer isTop);

    void incrementViewCount(Long id);

    List<ArticleVO> getHotArticles(Integer limit);

    /**
     * 归档用：返回全部已发布文章，不受分页 pageSize 上限限制。
     * 归档页需要按年份分组展示全部文章，走分页会被 pageSize 上限截断而静默丢数据。
     */
    List<ArticleVO> getArchiveArticles();

    List<ArticleVO> getLatestArticles(Integer limit);

    List<ArticleVO> getRelatedArticles(Long articleId, Long categoryId, Integer limit);

    /**
     * 获取上一篇（列表中更靠前的一篇）。
     *
     * @param id         当前文章 ID
     * @param tagId      限定在某个标签下的文章中查找；为 null 表示不限定标签
     * @param categoryId 限定在某个分类下的文章中查找；为 null 表示不限定分类
     * @return 上一篇；没有时返回 null
     */
    ArticleVO getPrevArticle(Long id, Long tagId, Long categoryId);

    /**
     * 获取下一篇（列表中更靠后的一篇）。
     *
     * @param id         当前文章 ID
     * @param tagId      限定在某个标签下的文章中查找；为 null 表示不限定标签
     * @param categoryId 限定在某个分类下的文章中查找；为 null 表示不限定分类
     * @return 下一篇；没有时返回 null
     */
    ArticleVO getNextArticle(Long id, Long tagId, Long categoryId);
}