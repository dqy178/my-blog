package com.blog.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计数据出参对象。
 */
@Data
public class StatsVO {

    /** 已发布文章数 */
    private Long articleCount;

    /** 分类总数 */
    private Long categoryCount;

    /** 标签总数 */
    private Long tagCount;

    /** 评论总数 */
    private Long commentCount;

    /** 总浏览量 */
    private Long viewCount;

    /** 访客数。当前无独立访客统计，复用总浏览量 */
    private Long visitorCount;

    /** 近 7 天浏览趋势，每项形如 {@code {date, views}}，数据来自 Redis 每日计数 */
    private List<Map<String, Object>> viewTrend;

    /** 近 7 天发布趋势，每项形如 {@code {date, count}}，按发布时间统计 */
    private List<Map<String, Object>> articleTrend;
}