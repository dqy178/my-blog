package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类入参对象。
 */
@Data
public class CategoryDTO {

    /** 主键 ID。创建时会被忽略（主键由数据库生成） */
    private Long id;

    /** 分类名称，必填，长度上限 50 字符。数据库上有唯一索引 */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50字符")
    private String name;

    /** 分类描述，长度上限 200 字符 */
    @Size(max = 200, message = "分类描述长度不能超过200字符")
    private String description;

    /** 分类图标标识 */
    private String icon;

    /** 排序值，越小越靠前，默认 0 */
    private Integer sort = 0;
}