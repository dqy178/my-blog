package com.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ArticleVO {

    private Long id;
    private String title;
    private String summary;
    private String content;
    private String cover;
    private Long categoryId;
    private String categoryName;
    private Integer status;
    private Integer isTop;
    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private Integer allowComment;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime publishTime;
    private List<String> tagNames;
    private List<TagVO> tags;
}