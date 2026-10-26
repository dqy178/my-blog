package com.blog.controller;

import com.blog.dto.ArticleQueryDTO;
import com.blog.service.ArticleService;
import com.blog.service.CategoryService;
import com.blog.service.TagService;
import com.blog.vo.ArticleVO;
import com.blog.vo.CategoryVO;
import com.blog.vo.TagVO;
import com.blog.common.Result;
import com.blog.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/front/articles")
@RequiredArgsConstructor
public class FrontArticleController {

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final TagService tagService;

    @GetMapping("/page")
    public Result<PageResult<ArticleVO>> getArticlePage(ArticleQueryDTO queryDTO) {
        queryDTO.setStatus(1);
        PageResult<ArticleVO> pageResult = articleService.getArticlePage(queryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    public Result<ArticleVO> getArticleDetail(@PathVariable Long id) {
        ArticleVO article = articleService.getPublishedArticleDetail(id);
        if (article == null) {
            return Result.failed(com.blog.common.ResultCode.NOT_FOUND, "文章不存在");
        }
        return Result.success(article);
    }

    @PostMapping("/{id}/view")
    public Result<Void> incrementView(@PathVariable Long id) {
        try {
            articleService.incrementViewCount(id);
        } catch (Exception e) {
            // 忽略所有异常，防止影响前台访问
        }
        return Result.success();
    }

    @GetMapping("/hot")
    public Result<List<ArticleVO>> getHotArticles(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(articleService.getHotArticles(limit));
    }

    @GetMapping("/latest")
    public Result<List<ArticleVO>> getLatestArticles(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(articleService.getLatestArticles(limit));
    }

    @GetMapping("/{id}/related")
    public Result<List<ArticleVO>> getRelatedArticles(
            @PathVariable Long id,
            @RequestParam Long categoryId,
            @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(articleService.getRelatedArticles(id, categoryId, limit));
    }

    @GetMapping("/{id}/prev")
    public Result<ArticleVO> getPrevArticle(@PathVariable Long id) {
        return Result.success(articleService.getPrevArticle(id));
    }

    @GetMapping("/{id}/next")
    public Result<ArticleVO> getNextArticle(@PathVariable Long id) {
        return Result.success(articleService.getNextArticle(id));
    }

    @GetMapping("/archive")
    public Result<List<ArticleVO>> getArchive() {
ArticleQueryDTO queryDTO = new ArticleQueryDTO();
queryDTO.setStatus(1);
queryDTO.setPageNum(1);
queryDTO.setPageSize(1000);
        return Result.success(articleService.getArticlePage(queryDTO).getList());
    }
}