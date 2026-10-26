package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ArticleDTO {

    private Long id;

    @NotBlank(message = "文章标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100字符")
    private String title;

    @Size(max = 300, message = "摘要长度不能超过300字符")
    private String summary;

    @NotBlank(message = "文章内容不能为空")
    private String content;

    private String cover;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    private Integer status;

    private Integer isTop;

    private Integer allowComment = 1;

    private List<Long> tagIds;
}