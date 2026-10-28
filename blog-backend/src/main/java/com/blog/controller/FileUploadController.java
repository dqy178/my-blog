package com.blog.controller;

import com.blog.common.Result;
import com.blog.utils.FileUploadUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台文件上传接口。
 *
 * <p>路径前缀 {@code /admin/files/**} 强制要求 {@code ROLE_ADMIN} 角色。
 * 上传文件的类型校验在 {@code FileUploadUtil} 中完成（基于文件头魔数）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadUtil fileUploadUtil;

    /** 上传文件访问域名的前缀，来自配置项 {@code file.upload.domain} */
    @Value("${file.upload.domain}")
    private String fileDomain;

    /**
     * 通用文件上传。
     *
     * @param file 上传文件
     * @return 包含可直接访问的 {@code url} 字段
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = fileUploadUtil.upload(file);
        Map<String, String> result = new HashMap<>();
        result.put("url", fileDomain + url);
        return Result.success(result);
    }

    /**
     * 编辑器专用上传接口。
     *
     * <p>返回 wangeditor MD 编辑器期望的 {@code {errno, data}} 结构，
     * 同时外层仍使用统一的 Result 包装。</p>
     */
    @PostMapping("/upload/editor")
    public Result<Map<String, Object>> uploadForEditor(@RequestParam("file") MultipartFile file) {
        String url = fileUploadUtil.upload(file);
        // 兼容 wangeditor MD 编辑器（errno=0 + data）的同时统一包装
        Map<String, Object> data = new HashMap<>();
        data.put("errno", 0);
        data.put("data", new String[]{fileDomain + url});
        return Result.success(data);
    }
}