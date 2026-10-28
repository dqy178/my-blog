package com.blog.utils;

import com.blog.common.BusinessException;
import com.blog.common.ResultCode;
import com.blog.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 算术验证码。
 *
 * <p>生成一道简单算术题，答案存 Redis（一次性、5 分钟过期），提交时用 captchaId 取回比对。
 * 用于阻挡「随手一个字符串就能通过」的占位校验，给批量注册/灌水抬高成本。</p>
 *
 * <p>注意：算术题对自动化脚本几乎没有防御力（脚本可自行获取并求解），
 * 因此它必须与频率限制配合使用才有意义。</p>
 */
@Component
@RequiredArgsConstructor
public class CaptchaUtil {

    /** Redis 键前缀：{@code captcha:{uuid}} */
    private static final String KEY_PREFIX = "captcha:";

    /** 验证码有效期（分钟）。超过该时间未使用则自动失效 */
    private static final long TTL_MINUTES = 5;

    /** 使用 SecureRandom 而非 Random：验证码答案需要不可预测 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisUtil redisUtil;

    /**
     * 生成一道新的算术验证码。
     *
     * <p>两个 1~9 的随机数相加，避免减法产生负数。答案存入 Redis，
     * 前端只拿到 {@code captchaId} 与题目文本，看不到答案。</p>
     */
    public CaptchaVO generate() {
        int a = 1 + RANDOM.nextInt(9);
        int b = 1 + RANDOM.nextInt(9);
        // 避免减法产生负数，统一用加法
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        redisUtil.set(KEY_PREFIX + captchaId, String.valueOf(a + b), TTL_MINUTES, TimeUnit.MINUTES);
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaId(captchaId);
        vo.setQuestion(a + " + " + b + " = ?");
        return vo;
    }

    /**
     * 校验验证码。无论通过与否都会立即失效（一次性使用），防止重复提交。
     *
     * <p>「先删后比对」是有意为之：若先比对再删，答错时验证码仍然有效，
     * 攻击者可以无限次尝试，直到撞出正确答案。</p>
     *
     * @param captchaId 验证码 ID，由 {@link #generate()} 返回
     * @param answer   用户提交的答案
     * @throws BusinessException 验证码缺失、过期或答案错误时抛出
     */
    public void verify(String captchaId, String answer) {
        if (captchaId == null || captchaId.isBlank() || answer == null || answer.isBlank()) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "请输入验证码");
        }
        String key = KEY_PREFIX + captchaId;
        Object expected = redisUtil.get(key);
        // 一次性使用：无论对错都先删除
        redisUtil.del(key);
        if (expected == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "验证码已过期，请刷新重试");
        }
        if (!String.valueOf(expected).equals(answer.trim())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "验证码错误");
        }
    }
}
