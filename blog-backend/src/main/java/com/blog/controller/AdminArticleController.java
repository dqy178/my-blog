package com.blog.controller;

import com.blog.dto.ArticleDTO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.service.ArticleService;
import com.blog.vo.ArticleVO;
import com.blog.common.Result;
import com.blog.common.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/articles")
@RequiredArgsConstructor
public class AdminArticleController {

    private final ArticleService articleService;

    @GetMapping("/page")
    public Result<PageResult<ArticleVO>> getArticlePage(ArticleQueryDTO queryDTO) {
        return Result.success(articleService.getArticlePage(queryDTO));
    }

    @GetMapping("/{id}")
    public Result<ArticleVO> getArticleDetail(@PathVariable Long id) {
        ArticleVO article = articleService.getArticleDetail(id);
        if (article == null) {
            return Result.failed(com.blog.common.ResultCode.NOT_FOUND, "文章不存在");
        }
        return Result.success(article);
    }

    @PostMapping
    public Result<ArticleVO> createArticle(@Valid @RequestBody ArticleDTO articleDTO) {
        return Result.success(articleService.createArticle(articleDTO));
    }

    @PutMapping("/{id}")
    public Result<ArticleVO> updateArticle(@PathVariable Long id, @Valid @RequestBody ArticleDTO articleDTO) {
        return Result.success(articleService.updateArticle(id, articleDTO));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteArticle(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return Result.success();
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDeleteArticles(@RequestParam("data") List<Long> ids) {
        articleService.batchDeleteArticles(ids);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateArticleStatus(@PathVariable Long id, @RequestParam Integer status) {
        articleService.updateArticleStatus(id, status);
        return Result.success();
    }

    @PutMapping("/batch/status")
    public Result<Void> batchUpdateArticleStatus(@RequestParam("data") List<Long> ids, @RequestParam Integer status) {
        articleService.batchUpdateArticleStatus(ids, status);
        return Result.success();
    }

    @PutMapping("/{id}/top")
    public Result<Void> updateArticleTop(@PathVariable Long id, @RequestParam Integer isTop) {
        articleService.updateArticleTop(id, isTop);
        return Result.success();
    }
}