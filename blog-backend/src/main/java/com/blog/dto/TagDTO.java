package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 标签入参对象。
 */
@Data
public class TagDTO {

    /** 主键 ID。创建时会被忽略（主键由数据库生成） */
    private Long id;

    /** 标签名称，必填，长度上限 30 字符。数据库上有唯一索引 */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 30, message = "标签名称长度不能超过30字符")
    private String name;

    /** 标签展示颜色（十六进制色值），长度上限 20 字符。为空时由服务端填入默认值 */
    @Size(max = 20, message = "颜色值长度不能超过20字符")
    private String color;
}