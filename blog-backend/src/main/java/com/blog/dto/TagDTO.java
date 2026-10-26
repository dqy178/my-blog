package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagDTO {

    private Long id;

    @NotBlank(message = "标签名称不能为空")
    @Size(max = 30, message = "标签名称长度不能超过30字符")
    private String name;

    @Size(max = 20, message = "颜色值长度不能超过20字符")
    private String color;
}