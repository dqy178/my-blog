package com.blog.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class FileUploadUtil {

    /**
     * 各图片格式的文件头魔数（magic number）。
     * Content-Type 是客户端可任意伪造的，不能只信它；
     * 真正判断文件内容必须看文件头字节。
     */
    private static final Map<String, byte[]> IMAGE_MAGIC_NUMBERS = Map.of(
            ".jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            ".jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            ".png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
            ".gif", new byte[]{0x47, 0x49, 0x46, 0x38},
            ".bmp", new byte[]{0x42, 0x4D}
    );

    /** WEBP 是 RIFF 容器，magic number 在偏移 8 处 */
    private static final byte[] WEBP_RIFF = new byte[]{0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_TAG = new byte[]{0x57, 0x45, 0x42, 0x50};

    @Value("${file.upload.path}")
    private String uploadPath;

    /**
     * 校验文件头是否与该扩展名对应的格式匹配。
     *
     * @param extension 扩展名（含点号）
     * @param header    文件头字节
     * @return 匹配返回 true
     */
    private static boolean matchesMagicNumber(String extension, byte[] header) {
        if (header == null || header.length < 4) {
            return false;
        }
        if (".webp".equals(extension)) {
            // 需要至少 12 字节才能读到 RIFF....WEBP
            if (header.length < 12) {
                return false;
            }
            for (int i = 0; i < WEBP_RIFF.length; i++) {
                if (header[i] != WEBP_RIFF[i]) {
                    return false;
                }
            }
            for (int i = 0; i < WEBP_TAG.length; i++) {
                if (header[8 + i] != WEBP_TAG[i]) {
                    return false;
                }
            }
            return true;
        }
        byte[] magic = IMAGE_MAGIC_NUMBERS.get(extension);
        if (magic == null) {
            return false;
        }
        if (header.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (header[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 根据文件头判定图片的真实格式，返回该格式的扩展名；不是允许的图片格式则返回 null。
     *
     * <p>刻意不参考客户端给的扩展名：PNG 内容存成 .jpg 是很常见的情况
     * （网页另存、聊天软件的表情包等），拿扩展名去比对文件头会误判成
     * 「文件内容与扩展名不符」而拒绝掉本来正常的图片。
     *
     * <p>安全性不受影响：判定依据是文件头而非文件名，非图片内容依然进不来；
     * 且存盘时用的扩展名由本方法决定，永远是安全的图片扩展名。</p>
     */
    /**
     * 根据文件头判定图片的真实格式，返回该格式的扩展名；不是允许的图片格式则返回 null。
     *
     * <p>刻意不参考客户端给的扩展名：PNG 内容存成 .jpg 是很常见的情况
     * （网页另存、聊天软件的表情包等），拿扩展名去比对文件头会误判成
     * 「文件内容与扩展名不符」而拒绝掉本来正常的图片。</p>
     *
     * <p>安全性不受影响：判定依据是文件头而非文件名，非图片内容依然进不来；
     * 且存盘时用的扩展名由本方法决定，永远是安全的图片扩展名。</p>
     *
     * <p>注意：{@code .jpg} 与 {@code .jpeg} 的文件头完全相同，统一存成 {@code .jpg}。</p>
     *
     * @param header 文件头字节（至少 12 字节）
     * @return 识别出的扩展名（含点号），非图片返回 null
     */
    static String detectImageExtension(byte[] header) {
        // .jpg 与 .jpeg 的文件头完全相同，统一存成 .jpg
        if (matchesMagicNumber(".jpg", header)) {
            return ".jpg";
        }
        for (String extension : List.of(".png", ".gif", ".bmp", ".webp")) {
            if (matchesMagicNumber(extension, header)) {
                return extension;
            }
        }
        return null;
    }

    /**
     * 执行文件上传：校验 → 生成文件名 → 落盘。
     *
     * <p>文件按 {@code yyyy/MM/dd/} 日期目录分桶，文件名用 UUID 随机生成，
     * 既避免同名覆盖，也杜绝了由客户端文件名引入的路径穿越风险。</p>
     *
     * @param file 上传的文件
     * @return 可直接访问的相对 URL（如 {@code /uploads/2026/10/28/xxx.png}）
     */
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new com.blog.common.BusinessException("上传文件不能为空");
        }

        // 读文件头：只有文件头才能证明内容确实是图片。
        // 一次 read 不保证读满，必须循环读满 12 字节，否则文件头不完整会被误判。
        byte[] header = new byte[12];
        int total = 0;
        try (java.io.InputStream in = file.getInputStream()) {
            while (total < header.length) {
                int n = in.read(header, total, header.length - total);
                if (n < 0) {
                    break;
                }
                total += n;
            }
        } catch (IOException e) {
            throw new com.blog.common.BusinessException("文件读取失败");
        }
        if (total < 4) {
            throw new com.blog.common.BusinessException("文件内容为空或不是有效的图片");
        }

        // 以文件头判定真实格式，存盘扩展名也用真实格式。
        // 这样「PNG 内容 + .jpg 文件名」能正常上传，而不是报「文件内容与扩展名不符」。
        String extension = detectImageExtension(header);
        if (extension == null) {
            throw new com.blog.common.BusinessException(
                    "只允许上传图片文件（jpg/png/gif/webp/bmp）");
        }

        // Content-Type 由客户端提供、可任意伪造，不能作为判定依据，
        // 这里只做宽松校验（是不是 image/*），不做与扩展名的严格比对：
        // 浏览器按真实内容报 Content-Type，与用户改过的文件名可能对不上。
        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase().startsWith("image/")) {
            throw new com.blog.common.BusinessException("只允许上传图片文件");
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