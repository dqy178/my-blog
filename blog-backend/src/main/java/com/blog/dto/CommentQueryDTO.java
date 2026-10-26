package com.blog.dto;

import com.blog.common.PageQuery;
import lombok.Data;

@Data
public class CommentQueryDTO extends PageQuery {

    private Long articleId;

    private Integer status;
}