/**
 * 分类相关的接口封装与类型定义。
 *
 * <p>类型与后端 {@code com.blog.vo.CategoryVO} / {@code com.blog.dto.CategoryDTO} 一一对应。</p>
 */
import request from '@/utils/request'

/** 分类出参 */
export interface CategoryVO {
  /** 分类 ID */
  id: number
  /** 父分类 ID。顶级分类为 undefined */
  parentId?: number
  /** 分类名称 */
  name: string
  /** 分类描述 */
  description: string
  /** 分类图标标识 */
  icon: string
  /** 排序值，越小越靠前 */
  sort: number
  /** 文章数（口径：仅统计已发布文章） */
  articleCount: number
  /** 真实被引用数（含草稿），供前端判断删除按钮是否可用 */
  referenceCount?: number
  /** 创建时间 */
  createTime: string
  /** 最后更新时间 */
  updateTime: string
  /** 子分类列表，装配分类树时返回 */
  children?: CategoryVO[]
}

/** 分类入参 */
export interface CategoryDTO {
  /** 主键 ID，新建时不需要 */
  id?: number
  /** 分类名称 */
  name: string
  /** 分类描述 */
  description: string
  /** 分类图标标识 */
  icon: string
  /** 排序值 */
  sort: number
}

export const categoryApi = {
  // 前台接口
  /** 获取全部分类（扁平列表） */
  getAllCategories() {
    return request.get<CategoryVO[]>('/front/categories')
  },

  /** 获取分类树 */
  getCategoryTree() {
    return request.get<CategoryVO[]>('/front/categories/tree')
  },

  /** 获取分类详情 */
  getCategoryById(id: number) {
    return request.get<CategoryVO>(`/front/categories/${id}`)
  },

  // 后台接口
  /** 后台获取全部分类 */
  getAdminCategories() {
    return request.get<CategoryVO[]>('/admin/categories')
  },

  /** 后台获取分类树 */
  getAdminCategoryTree() {
    return request.get<CategoryVO[]>('/admin/categories/tree')
  },

  /** 后台获取分类详情 */
  getAdminCategoryById(id: number) {
    return request.get<CategoryVO>(`/admin/categories/${id}`)
  },

  /** 新建分类 */
  createCategory(data: CategoryDTO) {
    return request.post<CategoryVO>('/admin/categories', data)
  },

  /** 更新分类 */
  updateCategory(id: number, data: CategoryDTO) {
    return request.put<CategoryVO>(`/admin/categories/${id}`, data)
  },

  /** 删除分类。若仍被文章引用（含草稿）后端会返回 409 */
  deleteCategory(id: number) {
    return request.delete(`/admin/categories/${id}`)
  },

  /** 批量删除分类。任一被引用则整体拒绝 */
  batchDeleteCategories(ids: number[]) {
    return request.deleteBatch('/admin/categories/batch', ids)
  }
}