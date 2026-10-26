package com.blog.service;
import java.util.List;

import com.blog.dto.ArticleDTO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.vo.ArticleVO;
import com.blog.common.PageResult;

import java.util.List;

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

    List<ArticleVO> getLatestArticles(Integer limit);

    List<ArticleVO> getRelatedArticles(Long articleId, Long categoryId, Integer limit);

    ArticleVO getPrevArticle(Long id);

    ArticleVO getNextArticle(Long id);
}