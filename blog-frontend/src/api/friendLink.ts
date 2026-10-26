import request from '@/utils/request'

export interface FriendLinkVO {
  id: number
  name: string
  url: string
  avatar: string
  description: string
  sort: number
  status: number
  createTime: string
  updateTime: string
}

export interface FriendLinkDTO {
  id?: number
  name: string
  url: string
  avatar: string
  description: string
  sort: number
  status: number
}

export const friendLinkApi = {
  // 前台接口
  getActiveFriendLinks() {
    return request.get<FriendLinkVO[]>('/front/friend-links')
  },

  // 后台接口
  getAllFriendLinks() {
    return request.get<FriendLinkVO[]>('/admin/friend-links')
  },

  getActiveFriendLinksAdmin() {
    return request.get<FriendLinkVO[]>('/admin/friend-links/active')
  },

  getFriendLinkById(id: number) {
    return request.get<FriendLinkVO>(`/admin/friend-links/${id}`)
  },

  createFriendLink(data: FriendLinkDTO) {
    return request.post<FriendLinkVO>('/admin/friend-links', data)
  },

  updateFriendLink(id: number, data: FriendLinkDTO) {
    return request.put<FriendLinkVO>(`/admin/friend-links/${id}`, data)
  },

  deleteFriendLink(id: number) {
    return request.delete(`/admin/friend-links/${id}`)
  },

  batchDeleteFriendLinks(ids: number[]) {
    return request.delete('/admin/friend-links/batch', { data: ids })
  },

  updateFriendLinkStatus(id: number, status: number) {
    return request.put(`/admin/friend-links/${id}/status`, null, { params: { status } })
  }
}