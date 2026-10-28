/**
 * API 通用类型
 */

/**
 * 分页结果。
 *
 * <p>与后端 {@code com.blog.common.PageResult} 对应。</p>
 */
export interface PageResult<T> {
  /** 记录总数 */
  total: number
  /** 当前页数据 */
  list: T[]
  /** 当前页码，从 1 开始 */
  pageNum: number
  /** 当前每页条数 */
  pageSize: number
  /** 总页数 */
  totalPage: number
}
