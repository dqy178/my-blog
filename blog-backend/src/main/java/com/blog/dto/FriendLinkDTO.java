package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 友链入参对象。
 */
@Data
public class FriendLinkDTO {

    /** 主键 ID。创建时会被忽略（主键由数据库生成） */
    private Long id;

    /** 友链名称，必填，长度上限 50 字符 */
    @NotBlank(message = "友链名称不能为空")
    @Size(max = 50, message = "名称长度不能超过50字符")
    private String name;

    /** 友链地址，必填，长度上限 200 字符，必须是合法的 http/https URL */
    @NotBlank(message = "友链地址不能为空")
    @Size(max = 200, message = "地址长度不能超过200字符")
    @Pattern(regexp = "^https?://[\\w.-]+(?::\\d+)?(?:/[\\w./\\-?=&%#]*)?$",
            message = "友链地址必须以 http:// 或 https:// 开头，且为合法 URL")
    private String url;

    /** 友链头像 URL */
    private String avatar;

    /** 友链描述，长度上限 200 字符 */
    @Size(max = 200, message = "描述长度不能超过200字符")
    private String description;

    /** 排序值，越小越靠前，默认 0 */
    private Integer sort = 0;

    /** 显示状态：1=显示，0=隐藏，默认显示 */
    private Integer status = 1;
}