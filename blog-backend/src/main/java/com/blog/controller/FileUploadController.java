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

@Slf4j
@RestController
@RequestMapping("/admin/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadUtil fileUploadUtil;

    @Value("${file.upload.domain}")
    private String fileDomain;

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = fileUploadUtil.upload(file);
        Map<String, String> result = new HashMap<>();
        result.put("url", fileDomain + url);
        return Result.success(result);
    }

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