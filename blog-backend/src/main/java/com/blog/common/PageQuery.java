package com.blog.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分页与排序查询参数基类。
 *
 * <p>作为 DTO 的父类被继承（如 {@code ArticleQueryDTO}、{@code CommentQueryDTO}），
 * Spring 会自动把同名请求参数绑定到这些字段上。</p>
 */
@Data
public class PageQuery {

    /** 页码，从 1 开始。非法值（null / ≤0）在 getter 中回落到 1 */
    @Schema(description = "页码", defaultValue = "1")
    private Integer pageNum = 1;

    /** 每页条数。上限为 100，超出会被夹取；非法值在 getter 中回落到 10 */
    @Schema(description = "每页大小", defaultValue = "10")
    private Integer pageSize = 10;

    /** 排序字段名，由各 Service 用白名单映射为具体的数据库列 */
    @Schema(description = "排序字段")
    private String sortField;

    /** 排序方向：asc 升序 / desc 降序 */
    @Schema(description = "排序方式 asc/desc", defaultValue = "desc")
    private String sortOrder = "desc";

    /**
     * 获取页码，屏蔽非法入参。
     *
     * <p>不直接返回字段值而是通过 getter 归一化，避免每个使用方重复判空。</p>
     */
    public Integer getPageNum() {
        return pageNum != null && pageNum > 0 ? pageNum : 1;
    }

    /**
     * 获取每页条数，屏蔽非法入参并限制上限。
     *
     * <p>上限 100 是为了防止单次查询拉取过多数据拖垮数据库；
     * 需要取全量数据（如归档列表）的场景应走专用接口，而不是调大 pageSize。</p>
     */
    public Integer getPageSize() {
        return pageSize != null && pageSize > 0 ? Math.min(pageSize, 100) : 10;
    }
}