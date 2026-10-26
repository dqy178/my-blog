import request from '@/utils/request'

export interface CategoryVO {
  id: number
  parentId?: number
  name: string
  description: string
  icon: string
  sort: number
  articleCount: number
  referenceCount?: number
  createTime: string
  updateTime: string
  children?: CategoryVO[]
}

export interface CategoryDTO {
  id?: number
  name: string
  description: string
  icon: string
  sort: number
}

export const categoryApi = {
  // 前台接口
  getAllCategories() {
    return request.get<CategoryVO[]>('/front/categories')
  },

  getCategoryTree() {
    return request.get<CategoryVO[]>('/front/categories/tree')
  },

  getCategoryById(id: number) {
    return request.get<CategoryVO>(`/front/categories/${id}`)
  },

  // 后台接口
  getAdminCategories() {
    return request.get<CategoryVO[]>('/admin/categories')
  },

  getAdminCategoryTree() {
    return request.get<CategoryVO[]>('/admin/categories/tree')
  },

  getAdminCategoryById(id: number) {
    return request.get<CategoryVO>(`/admin/categories/${id}`)
  },

  createCategory(data: CategoryDTO) {
    return request.post<CategoryVO>('/admin/categories', data)
  },

  updateCategory(id: number, data: CategoryDTO) {
    return request.put<CategoryVO>(`/admin/categories/${id}`, data)
  },

  deleteCategory(id: number) {
    return request.delete(`/admin/categories/${id}`)
  },

  batchDeleteCategories(ids: number[]) {
    return request.delete('/admin/categories/batch', { data: ids })
  }
}