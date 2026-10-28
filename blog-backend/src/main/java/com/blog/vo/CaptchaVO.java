package com.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 验证码
 */
@Data
@Schema(description = "验证码")
public class CaptchaVO {

    @Schema(description = "验证码 ID，提交时需原样带回")
    private String captchaId;

    @Schema(description = "题目，如 3 + 5 = ?")
    private String question;
}
