package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FriendLinkDTO {

    private Long id;

    @NotBlank(message = "友链名称不能为空")
    @Size(max = 50, message = "名称长度不能超过50字符")
    private String name;

    @NotBlank(message = "友链地址不能为空")
    @Size(max = 200, message = "地址长度不能超过200字符")
    @Pattern(regexp = "^https?://[\\w.-]+(?::\\d+)?(?:/[\\w./\\-?=&%#]*)?$",
            message = "友链地址必须以 http:// 或 https:// 开头，且为合法 URL")
    private String url;

    private String avatar;

    @Size(max = 200, message = "描述长度不能超过200字符")
    private String description;

    private Integer sort = 0;

    private Integer status = 1;
}