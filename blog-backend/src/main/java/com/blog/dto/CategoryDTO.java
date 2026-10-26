package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryDTO {

    private Long id;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50字符")
    private String name;

    @Size(max = 200, message = "分类描述长度不能超过200字符")
    private String description;

    private String icon;

    private Integer sort = 0;
}