/**
 * 标签相关的接口封装与类型定义。
 *
 * <p>类型与后端 {@code com.blog.vo.TagVO} / {@code com.blog.dto.TagDTO} 一一对应。</p>
 */
import request from '@/utils/request'

/** 标签出参 */
export interface TagVO {
  /** 标签 ID */
  id: number
  /** 标签名称 */
  name: string
  /** 标签展示颜色（十六进制色值） */
  color: string
  /** 文章数（口径：仅统计已发布文章） */
  articleCount: number
  /** 真实被引用数（含草稿），供前端判断删除按钮是否可用 */
  referenceCount?: number
  /** 创建时间 */
  createTime: string
  /** 最后更新时间 */
  updateTime: string
}

/** 标签入参 */
export interface TagDTO {
  /** 主键 ID，新建时不需要 */
  id?: number
  /** 标签名称 */
  name: string
  /** 标签展示颜色。为空时后端填入默认值 */
  color: string
}

export const tagApi = {
  // 前台接口
  /** 获取全部标签 */
  getAllTags() {
    return request.get<TagVO[]>('/front/tags')
  },

  /** 获取标签云数据 */
  getTagCloud() {
    return request.get<TagVO[]>('/front/tags/cloud')
  },

  /** 获取标签详情 */
  getTagById(id: number) {
    return request.get<TagVO>(`/front/tags/${id}`)
  },

  // 后台接口
  /** 后台获取全部标签 */
  getAdminTags() {
    return request.get<TagVO[]>('/admin/tags')
  },

  /** 后台获取标签列表（与 getAdminTags 指向同一接口，保留以对齐命名习惯） */
  getAdminTagCloud() {
    return request.get<TagVO[]>('/admin/tags')
  },

  /** 后台获取标签详情 */
  getAdminTagById(id: number) {
    return request.get<TagVO>(`/admin/tags/${id}`)
  },

  /** 新建标签 */
  createTag(data: TagDTO) {
    return request.post<TagVO>('/admin/tags', data)
  },

  /** 更新标签 */
  updateTag(id: number, data: TagDTO) {
    return request.put<TagVO>(`/admin/tags/${id}`, data)
  },

  /** 删除标签 */
  deleteTag(id: number) {
    return request.delete(`/admin/tags/${id}`)
  },

  /** 批量删除标签 */
  batchDeleteTags(ids: number[]) {
    return request.deleteBatch('/admin/tags/batch', ids)
  }
}