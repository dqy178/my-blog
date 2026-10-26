package com.blog.dto;

import com.blog.common.PageQuery;
import lombok.Data;

@Data
public class ArticleQueryDTO extends PageQuery {

    private Long categoryId;

    private Long tagId;

    private String keyword;

    private Integer status;

    private Integer isTop;
}