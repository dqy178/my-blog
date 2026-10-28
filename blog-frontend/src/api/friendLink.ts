/**
 * 友链相关的接口封装与类型定义。
 *
 * <p>类型与后端 {@code com.blog.vo.FriendLinkVO} / {@code com.blog.dto.FriendLinkDTO} 一一对应。</p>
 */
import request from '@/utils/request'

/** 友链出参 */
export interface FriendLinkVO {
  /** 友链 ID */
  id: number
  /** 友链名称 */
  name: string
  /** 友链地址 */
  url: string
  /** 友链头像 URL */
  avatar: string
  /** 友链描述 */
  description: string
  /** 排序值，越小越靠前 */
  sort: number
  /** 显示状态：1=显示，0=隐藏 */
  status: number
  /** 创建时间 */
  createTime: string
  /** 最后更新时间 */
  updateTime: string
}

/** 友链入参 */
export interface FriendLinkDTO {
  /** 主键 ID，新建时不需要 */
  id?: number
  /** 友链名称 */
  name: string
  /** 友链地址，必须是合法的 http/https URL */
  url: string
  /** 友链头像 URL */
  avatar: string
  /** 友链描述 */
  description: string
  /** 排序值 */
  sort: number
  /** 显示状态：1=显示，0=隐藏 */
  status: number
}

export const friendLinkApi = {
  // 前台接口
  /** 获取需要展示的友链（仅 status=1） */
  getActiveFriendLinks() {
    return request.get<FriendLinkVO[]>('/front/friend-links')
  },

  // 后台接口
  /** 后台获取全部友链（含已隐藏） */
  getAllFriendLinks() {
    return request.get<FriendLinkVO[]>('/admin/friend-links')
  },

  /** 后台获取需要展示的友链 */
  getActiveFriendLinksAdmin() {
    return request.get<FriendLinkVO[]>('/admin/friend-links/active')
  },

  /** 后台获取友链详情 */
  getFriendLinkById(id: number) {
    return request.get<FriendLinkVO>(`/admin/friend-links/${id}`)
  },

  /** 新建友链 */
  createFriendLink(data: FriendLinkDTO) {
    return request.post<FriendLinkVO>('/admin/friend-links', data)
  },

  /** 更新友链 */
  updateFriendLink(id: number, data: FriendLinkDTO) {
    return request.put<FriendLinkVO>(`/admin/friend-links/${id}`, data)
  },

  /** 删除友链 */
  deleteFriendLink(id: number) {
    return request.delete(`/admin/friend-links/${id}`)
  },

  /** 批量删除友链 */
  batchDeleteFriendLinks(ids: number[]) {
    return request.deleteBatch('/admin/friend-links/batch', ids)
  },

  /** 修改显示状态：1=显示，0=隐藏 */
  updateFriendLinkStatus(id: number, status: number) {
    return request.put(`/admin/friend-links/${id}/status`, null, { params: { status } })
  }
}