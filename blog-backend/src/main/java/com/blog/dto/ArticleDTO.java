package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 文章入参对象。
 *
 * <p>校验注解上的 message 会被 {@code GlobalExceptionHandler} 收集后返回给前端，
 * 因此这里的文案就是用户最终看到的提示。</p>
 *
 * <p>注意 {@code status} 与 {@code tagIds} 的 null 语义：null 表示「本次请求不修改该字段」，
 * 与「清空标签」（传空数组）是两种不同含义，见
 * {@code ArticleServiceImpl.updateArticle} 的处理。</p>
 */
@Data
public class ArticleDTO {

    /** 主键 ID。创建时会被忽略（主键由数据库生成），更新时也以路径参数为准 */
    private Long id;

    /** 标题，必填，长度上限 100 字符 */
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100字符")
    private String title;

    /** 摘要，长度上限 300 字符 */
    @Size(max = 300, message = "摘要长度不能超过300字符")
    private String summary;

    /** 正文（Markdown 原文），必填 */
    @NotBlank(message = "文章内容不能为空")
    private String content;

    /** 封面图 URL */
    private String cover;

    /** 所属分类 ID，必填 */
    @NotNull(message = "分类不能为空")
    private Long categoryId;

    /** 状态：0=草稿，1=发布，2=回收站。为 null 时表示不修改原状态 */
    private Integer status;

    /** 是否置顶：1=是，0=否 */
    private Integer isTop;

    /** 是否允许评论：1=允许，0=禁止，默认允许 */
    private Integer allowComment = 1;

    /** 标签 ID 列表。为 null 表示不修改原有标签，传空数组表示清空所有标签 */
    private List<Long> tagIds;
}