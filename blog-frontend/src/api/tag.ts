import request from '@/utils/request'

export interface TagVO {
  id: number
  name: string
  color: string
  articleCount: number
  referenceCount?: number
  createTime: string
  updateTime: string
}

export interface TagDTO {
  id?: number
  name: string
  color: string
}

export const tagApi = {
  // 前台接口
  getAllTags() {
    return request.get<TagVO[]>('/front/tags')
  },

  getTagCloud() {
    return request.get<TagVO[]>('/front/tags/cloud')
  },

  getTagById(id: number) {
    return request.get<TagVO>(`/front/tags/${id}`)
  },

  // 后台接口
  getAdminTags() {
    return request.get<TagVO[]>('/admin/tags')
  },

  getAdminTagCloud() {
    return request.get<TagVO[]>('/admin/tags')
  },

  getAdminTagById(id: number) {
    return request.get<TagVO>(`/admin/tags/${id}`)
  },

  createTag(data: TagDTO) {
    return request.post<TagVO>('/admin/tags', data)
  },

  updateTag(id: number, data: TagDTO) {
    return request.put<TagVO>(`/admin/tags/${id}`, data)
  },

  deleteTag(id: number) {
    return request.delete(`/admin/tags/${id}`)
  },

  batchDeleteTags(ids: number[]) {
    return request.delete('/admin/tags/batch', { data: ids })
  }
}