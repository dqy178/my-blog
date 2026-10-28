package com.blog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.entity.Article;
import com.blog.entity.Comment;
import com.blog.service.CategoryService;
import com.blog.service.TagService;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CommentMapper;
import com.blog.utils.RedisUtil;
import com.blog.vo.StatsVO;
import com.blog.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 后台仪表盘统计接口。
 *
 * <p>返回文章/分类/标签/评论总数、总浏览量，以及近 7 天的浏览趋势与发布趋势，
 * 供前台 ECharts 图表渲染。</p>
 */
@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final CategoryService categoryService;
    private final TagService tagService;

    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final RedisUtil redisUtil;

    /**
     * 获取统计概览。
     */
    @GetMapping("/overview")
    public Result<StatsVO> getOverview() {
        StatsVO stats = new StatsVO();

        LambdaQueryWrapper<Article> articleWrapper = new LambdaQueryWrapper<>();
        articleWrapper.eq(Article::getDeleted, 0).eq(Article::getStatus, 1);
        List<Article> articles = articleMapper.selectList(articleWrapper);

        long articleCount = articles.size();

        // 浏览量直接存库，直接求和即可
        long totalViewCount = articles.stream()
                .mapToLong(a -> a.getViewCount() == null ? 0L : a.getViewCount())
                .sum();

        stats.setArticleCount(articleCount);
        stats.setCategoryCount((long) categoryService.getAllCategories().size());
        stats.setTagCount((long) tagService.getAllTags().size());
        LambdaQueryWrapper<Comment> commentWrapper = new LambdaQueryWrapper<>();
        commentWrapper.eq(Comment::getDeleted, 0);
        stats.setCommentCount((long) commentMapper.selectCount(commentWrapper));
        stats.setViewCount(totalViewCount);
        stats.setVisitorCount(totalViewCount);

        stats.setViewTrend(getViewTrend(articles));
        stats.setArticleTrend(getArticleTrend(articles));
        return Result.success(stats);
    }

    /**
     * 统计近 7 天的每日浏览量。
     *
     * <p>数据来自 Redis 的每日计数键 {@code view:daily:{yyyy-MM-dd}}；
     * Redis 不可用时该点记 0，不影响整体返回。</p>
     */
    private List<Map<String, Object>> getViewTrend(List<Article> articles) {
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 6; i >= 0; i--) {
            String date = now.minusDays(i).toLocalDate().toString();
            Map<String, Object> item = new HashMap<>();
            item.put("date", date);
            try {
                Object v = redisUtil.get("view:daily:" + date);
                long count = v == null ? 0L : Long.parseLong(v.toString());
                item.put("views", count);
            } catch (Exception e) {
                item.put("views", 0L);
            }
            trend.add(item);
        }
        return trend;
    }

    /**
     * 统计近 7 天的每日发布文章数。
     *
     * <p>以发布时间为准；发布时间为空时回落到创建时间。</p>
     */
    private List<Map<String, Object>> getArticleTrend(List<Article> articles) {
        Map<String, Long> dailyCount = articles.stream()
                .filter(a -> a.getStatus() != null && a.getStatus() == 1)
                .collect(Collectors.groupingBy(
                        a -> {
                            java.time.LocalDateTime pt = a.getPublishTime();
                            if (pt == null) pt = a.getCreateTime();
                            return pt.toLocalDate().toString();
                        },
                        Collectors.counting()
                ));
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 6; i >= 0; i--) {
            String date = now.minusDays(i).toLocalDate().toString();
            Map<String, Object> item = new HashMap<>();
            item.put("date", date);
            item.put("count", dailyCount.getOrDefault(date, 0L));
            trend.add(item);
        }
        return trend;
    }
}