package com.blog.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Component
public class FileUploadUtil {

    @Value("${file.upload.path}")
    private String uploadPath;

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new com.blog.common.BusinessException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        String relativePath = datePath + "/" + fileName;

        // 1. 确保 base 结尾有分隔符
        String base = uploadPath;
        if (!base.endsWith("/") && !base.endsWith("\\")) {
            base += "/";
        }

        // 2. 转成绝对路径(相对路径会被 MultipartFile.transferTo 解析到 Tomcat 临时目录)
        File baseDir = new File(base);
        if (!baseDir.isAbsolute()) {
            baseDir = new File(baseDir.getAbsolutePath());
        }

        // 3. 用 File 构造拼路径,自动处理分隔符
        File dest = new File(baseDir, relativePath);
        if (!dest.getParentFile().exists()) {
            dest.getParentFile().mkdirs();
        }

        try {
            file.transferTo(dest);
            log.info("[FileUpload] 上传成功: {}", dest.getAbsolutePath());
            return "/uploads/" + relativePath.replace("\\", "/");
        } catch (IOException e) {
            // 把实际目标路径和堆栈都打出来,下次失败一眼定位
            log.error("[FileUpload] 上传失败, 目标路径: {}", dest.getAbsolutePath(), e);
            throw new com.blog.common.BusinessException("文件上传失败");
        }
    }
}