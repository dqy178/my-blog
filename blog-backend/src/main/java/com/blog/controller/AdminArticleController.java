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

/**
 * 后台文章管理接口。
 *
 * <p>路径前缀 {@code /admin/**} 会由 JWT 拦截器强制校验 {@code ROLE_ADMIN} 角色，
 * 普通用户访问返回 403。</p>
 */
@RestController
@RequestMapping("/admin/articles")
@RequiredArgsConstructor
public class AdminArticleController {

    private final ArticleService articleService;

    /**
     * 分页查询文章（不强制过滤状态，便于管理草稿）。
     */
    @GetMapping("/page")
    public Result<PageResult<ArticleVO>> getArticlePage(ArticleQueryDTO queryDTO) {
        return Result.success(articleService.getArticlePage(queryDTO));
    }

    /**
     * 获取文章详情（含未发布与草稿）。
     */
    @GetMapping("/{id}")
    public Result<ArticleVO> getArticleDetail(@PathVariable Long id) {
        ArticleVO article = articleService.getArticleDetail(id);
        if (article == null) {
            return Result.failed(com.blog.common.ResultCode.NOT_FOUND, "文章不存在");
        }
        return Result.success(article);
    }

    /**
     * 新建文章。
     */
    @PostMapping
    public Result<ArticleVO> createArticle(@Valid @RequestBody ArticleDTO articleDTO) {
        return Result.success(articleService.createArticle(articleDTO));
    }

    /**
     * 编辑文章。
     */
    @PutMapping("/{id}")
    public Result<ArticleVO> updateArticle(@PathVariable Long id, @Valid @RequestBody ArticleDTO articleDTO) {
        return Result.success(articleService.updateArticle(id, articleDTO));
    }

    /**
     * 删除文章（逻辑删除）。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteArticle(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return Result.success();
    }

    /**
     * 批量删除文章。
     *
     * @param ids 文章 ID 列表，逗号分隔（如 {@code ?data=1,2,3}）
     */
    @DeleteMapping("/batch")
    public Result<Void> batchDeleteArticles(@RequestParam("data") List<Long> ids) {
        articleService.batchDeleteArticles(ids);
        return Result.success();
    }

    /**
     * 修改文章状态：0=草稿，1=发布，2=回收站。
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateArticleStatus(@PathVariable Long id, @RequestParam Integer status) {
        articleService.updateArticleStatus(id, status);
        return Result.success();
    }

    /**
     * 批量修改文章状态。
     */
    @PutMapping("/batch/status")
    public Result<Void> batchUpdateArticleStatus(@RequestParam("data") List<Long> ids, @RequestParam Integer status) {
        articleService.batchUpdateArticleStatus(ids, status);
        return Result.success();
    }

    /**
     * 设置/取消置顶。
     */
    @PutMapping("/{id}/top")
    public Result<Void> updateArticleTop(@PathVariable Long id, @RequestParam Integer isTop) {
        articleService.updateArticleTop(id, isTop);
        return Result.success();
    }
}