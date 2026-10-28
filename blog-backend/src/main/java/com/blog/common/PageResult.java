package com.blog.common;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页结果包装。
 *
 * <p>前端 {@code PageResult<T>} 类型与此一一对应（字段名 total / list / pageNum /
 * pageSize / totalPage）。</p>
 *
 * @param <T> 列表元素类型
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记录总数（满足条件的总条数，不受当前页影响） */
    private Long total;

    /** 当前页的数据列表 */
    private List<T> list;

    /** 当前页码，从 1 开始 */
    private Integer pageNum;

    /** 当前每页条数 */
    private Integer pageSize;

    /** 总页数，由 total 与 pageSize 计算得出 */
    private Integer totalPage;

    /**
     * 构造分页结果并自动计算总页数。
     *
     * <p>总页数用 {@code ceil(total / pageSize)} 计算；
     * 注意 pageSize 必须非零，否则会触发除零异常——调用方应传归一化后的值。</p>
     */
    public PageResult(Long total, List<T> list, Integer pageNum, Integer pageSize) {
        this.total = total;
        this.list = list;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.totalPage = (int) Math.ceil((double) total / pageSize);
    }
}