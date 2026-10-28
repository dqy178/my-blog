package com.blog.controller;

import com.blog.dto.ArticleQueryDTO;
import com.blog.service.ArticleService;
import com.blog.vo.ArticleVO;
import com.blog.common.Result;
import com.blog.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台文章接口。
 *
 * <p>全部为公开访问（无需登录）。所有查询都强制过滤 {@code status = 1}，
 * 保证草稿不会泄露给读者。</p>
 */
@RestController
@RequestMapping("/front/articles")
@RequiredArgsConstructor
public class FrontArticleController {

    private final ArticleService articleService;

    /**
     * 分页查询已发布文章。
     *
     * @param queryDTO 查询条件（分类、标签、关键词、排序、分页）
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<ArticleVO>> getArticlePage(ArticleQueryDTO queryDTO) {
        queryDTO.setStatus(1);
        PageResult<ArticleVO> pageResult = articleService.getArticlePage(queryDTO);
        return Result.success(pageResult);
    }

    /**
     * 获取文章详情。
     *
     * @param id 文章 ID
     * @return 文章信息；不存在或未发布时返回 404
     */
    @GetMapping("/{id}")
    public Result<ArticleVO> getArticleDetail(@PathVariable Long id) {
        ArticleVO article = articleService.getPublishedArticleDetail(id);
        if (article == null) {
            return Result.failed(com.blog.common.ResultCode.NOT_FOUND, "文章不存在");
        }
        return Result.success(article);
    }

    /**
     * 记录一次浏览。
     *
     * <p>统计失败不影响页面访问，因此内部吞掉所有异常并统一返回成功。</p>
     */
    @PostMapping("/{id}/view")
    public Result<Void> incrementView(@PathVariable Long id) {
        try {
            articleService.incrementViewCount(id);
        } catch (Exception e) {
            // 忽略所有异常，防止影响前台访问
        }
        return Result.success();
    }

    /**
     * 获取热门文章。
     *
     * @param limit 返回条数，默认 10，上限 50
     */
    @GetMapping("/hot")
    public Result<List<ArticleVO>> getHotArticles(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(articleService.getHotArticles(limit));
    }

    /**
     * 获取最新文章。
     *
     * @param limit 返回条数，默认 10，上限 50
     */
    @GetMapping("/latest")
    public Result<List<ArticleVO>> getLatestArticles(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(articleService.getLatestArticles(limit));
    }

    /**
     * 获取相关推荐文章（同分类，排除当前文章）。
     *
     * @param id         当前文章 ID
     * @param categoryId 分类 ID
     * @param limit      返回条数，默认 5，上限 20
     */
    @GetMapping("/{id}/related")
    public Result<List<ArticleVO>> getRelatedArticles(
            @PathVariable Long id,
            @RequestParam Long categoryId,
            @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(articleService.getRelatedArticles(id, categoryId, limit));
    }

    /**
     * 获取上一篇（列表中更靠前的一篇）。
     *
     * <p>{@code tagId} / {@code categoryId} 为可选作用域参数：
     * 从标签页或分类页进入时传入，上下文就会在对应集合内切换；
     * 不传则为全站范围。</p>
     *
     * @param id         当前文章 ID
     * @param tagId      限定标签；可为空
     * @param categoryId 限定分类；可为空
     * @return 文章信息；没有上一篇时 data 为 null
     */
    @GetMapping("/{id}/prev")
    public Result<ArticleVO> getPrevArticle(@PathVariable Long id,
                                            @RequestParam(required = false) Long tagId,
                                            @RequestParam(required = false) Long categoryId) {
        return Result.success(articleService.getPrevArticle(id, tagId, categoryId));
    }

    /**
     * 获取下一篇（列表中更靠后的一篇）。
     *
     * @param id         当前文章 ID
     * @param tagId      限定标签；可为空
     * @param categoryId 限定分类；可为空
     * @return 文章信息；没有下一篇时 data 为 null
     */
    @GetMapping("/{id}/next")
    public Result<ArticleVO> getNextArticle(@PathVariable Long id,
                                            @RequestParam(required = false) Long tagId,
                                            @RequestParam(required = false) Long categoryId) {
        return Result.success(articleService.getNextArticle(id, tagId, categoryId));
    }

    /**
     * 获取归档数据（按年份分组所需的全部已发布文章）。
     */
    @GetMapping("/archive")
    public Result<List<ArticleVO>> getArchive() {
        // 走专用方法：分页接口的 pageSize 上限是 100，
        // 用分页取归档会在文章超过 100 篇时静默丢数据。
        return Result.success(articleService.getArchiveArticles());
    }
}