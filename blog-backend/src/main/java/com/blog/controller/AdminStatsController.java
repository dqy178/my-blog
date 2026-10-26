package com.blog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.entity.Article;
import com.blog.entity.Comment;
import com.blog.service.ArticleService;
import com.blog.service.CategoryService;
import com.blog.service.CommentService;
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

@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final CommentService commentService;
    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final RedisUtil redisUtil;

    private static final String VIEW_COUNT_PREFIX = "article:view:";

    @GetMapping("/overview")
    public Result<StatsVO> getOverview() {
        StatsVO stats = new StatsVO();

        LambdaQueryWrapper<Article> articleWrapper = new LambdaQueryWrapper<>();
        articleWrapper.eq(Article::getDeleted, 0);
        List<Article> articles = articleMapper.selectList(articleWrapper);

        long articleCount = articles.size();

        // 数据库 view_count 求和
        long dbViewCount = articles.stream()
                .mapToLong(a -> a.getViewCount() == null ? 0L : a.getViewCount())
                .sum();

        // Redis 增量求和
        long redisViewCount = 0;
        if (!articles.isEmpty()) {
            List<String> viewKeys = articles.stream()
                    .map(a -> VIEW_COUNT_PREFIX + a.getId())
                    .collect(Collectors.toList());
            List<Object> redisCounts = redisUtil.multiGet(viewKeys);
            if (redisCounts != null) {
                for (Object v : redisCounts) {
                    if (v != null) {
                        try {
                            redisViewCount += Long.parseLong(v.toString());
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
            }
        }

        long totalViewCount = dbViewCount + redisViewCount;

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