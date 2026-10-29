package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 站内知识问答的入参。
 */
@Data
public class RagAskDTO {

    /** 用户问题，必填 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过500字符")
    private String question;

    /**
     * 期望召回的块数量。
     *
     * <p>为空时用配置里的默认值；显式传值时会被限制在 1~10，
     * 防止前端传一个很大的值把上下文预算撑爆。</p>
     */
    private Integer topK;
}
