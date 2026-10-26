package com.blog.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StatsVO {

    private Long articleCount;
    private Long categoryCount;
    private Long tagCount;
    private Long commentCount;
    private Long viewCount;
    private Long visitorCount;
    private List<Map<String, Object>> viewTrend;
    private List<Map<String, Object>> articleTrend;
}