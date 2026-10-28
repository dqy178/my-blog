package com.blog.dto;

import com.blog.common.PageQuery;
import lombok.Data;

/**
 * 文章查询条件。
 *
 * <p>继承 {@link PageQuery} 获得分页与排序参数，其余为可选的过滤条件。
 * 所有字段均为 null 时表示不加过滤。</p>
 */
@Data
public class ArticleQueryDTO extends PageQuery {

    /** 按分类过滤 */
    private Long categoryId;

    /** 按标签过滤。内部会先查出该标签下的文章 ID 集合再做 IN 过滤 */
    private Long tagId;

    /** 标题关键词，模糊匹配 */
    private String keyword;

    /** 按状态过滤：0=草稿，1=发布，2=回收站。前台接口会强制覆盖为 1 */
    private Integer status;

    /** 按置顶状态过滤：1=置顶，0=不置顶 */
    private Integer isTop;
}