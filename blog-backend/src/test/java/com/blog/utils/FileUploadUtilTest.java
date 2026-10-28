package com.blog.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 图片上传的真实格式判定测试。
 *
 * <p>覆盖的场景：用户拿到的图片文件扩展名与真实格式不一致
 * （PNG 内容存成 .jpg，网页另存、聊天软件表情包很常见），
 * 此时应当按真实格式入库，而不是报「文件内容与扩展名不符」。</p>
 */
class FileUploadUtilTest {

    /** 按十六进制字符串构造文件头，例如 "89504E47" */
    private static byte[] header(String hex) {
        byte[] h = new byte[12];
        for (int i = 0; i < hex.length() / 2 && i < h.length; i++) {
            h[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return h;
    }

    @Test
    @DisplayName("PNG 文件头应识别为 .png")
    void detectImageExtension_png_shouldReturnPng() {
        assertEquals(".png", FileUploadUtil.detectImageExtension(header("89504E470D0A1A0A")));
    }

    @Test
    @DisplayName("JPEG 文件头应识别为 .jpg")
    void detectImageExtension_jpeg_shouldReturnJpg() {
        assertEquals(".jpg", FileUploadUtil.detectImageExtension(header("FFD8FFE0")));
    }

    @Test
    @DisplayName("GIF 文件头应识别为 .gif")
    void detectImageExtension_gif_shouldReturnGif() {
        assertEquals(".gif", FileUploadUtil.detectImageExtension(header("474946383961")));
    }

    @Test
    @DisplayName("BMP 文件头应识别为 .bmp")
    void detectImageExtension_bmp_shouldReturnBmp() {
        assertEquals(".bmp", FileUploadUtil.detectImageExtension(header("424D3600")));
    }

    @Test
    @DisplayName("WEBP 文件头应识别为 .webp")
    void detectImageExtension_webp_shouldReturnWebp() {
        assertEquals(".webp", FileUploadUtil.detectImageExtension(header("524946461A00000057454250")));
    }

    @Test
    @DisplayName("非图片内容（HTML 文本）应识别失败")
    void detectImageExtension_html_shouldReturnNull() {
        byte[] text = "<!DOCTYPE html><html>".getBytes(StandardCharsets.UTF_8);

        assertNull(FileUploadUtil.detectImageExtension(text));
    }

    @Test
    @DisplayName("SVG 文本内容应识别失败（不能当图片上传，避免存储型 XSS）")
    void detectImageExtension_svg_shouldReturnNull() {
        byte[] text = "<svg xmlns=\"http://www.w3.org/2000/svg\">".getBytes(StandardCharsets.UTF_8);

        assertNull(FileUploadUtil.detectImageExtension(text));
    }

    @Test
    @DisplayName("文件头不足 4 字节应识别失败")
    void detectImageExtension_tooShort_shouldReturnNull() {
        assertNull(FileUploadUtil.detectImageExtension(new byte[0]));
        assertNull(FileUploadUtil.detectImageExtension(new byte[]{0x11, 0x22}));
    }
}
