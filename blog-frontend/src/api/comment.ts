import request from '@/utils/request'

export interface CommentVO {
  id: number
  articleId: number
  parentId: number
  userId: number
  nickname: string
  email: string
  website: string
  imageUrl: string
  avatar: string
  content: string
  ip: string
  address: string
  status: number
  isAdmin: number
  createTime: string
  updateTime: string
  children: CommentVO[]
  userNickname: string
  userAvatar: string
  articleTitle?: string
}

export interface CommentDTO {
  id?: number
  articleId: number
  parentId?: number
  content: string
  nickname?: string
  email?: string
  website?: string
  imageUrl?: string
  captcha?: string
}

export interface CommentQueryDTO {
  pageNum: number
  pageSize: number
  articleId?: number
  status?: number
  sortField?: string
  sortOrder?: string
}

export interface PageResult<T> {
  total: number
  list: T[]
  pageNum: number
  pageSize: number
  totalPage: number
}

export const commentApi = {
  // 前台接口
  getCommentsByArticleId(articleId: number) {
    return request.get<CommentVO[]>(`/front/comments/article/${articleId}`)
  },

  getCommentPage(params: CommentQueryDTO) {
    return request.get<PageResult<CommentVO>>('/front/comments/page', params)
  },

  createComment(data: CommentDTO) {
    return request.post<CommentVO>('/front/comments', data)
  },

  // 后台接口
  getAdminCommentPage(params: CommentQueryDTO) {
    return request.get<PageResult<CommentVO>>('/admin/comments/page', params)
  },

  getAdminCommentsByArticleId(articleId: number) {
    return request.get<CommentVO[]>(`/admin/comments/article/${articleId}`)
  },

  adminReplyComment(id: number, data: CommentDTO) {
    return request.post<CommentVO>(`/admin/comments/${id}/reply`, data)
  },

  deleteComment(id: number) {
    return request.delete(`/admin/comments/${id}`)
  },

  batchDeleteComments(ids: number[]) {
    return request.delete('/admin/comments/batch', { data: ids })
  },

  updateCommentStatus(id: number, status: number) {
    return request.put(`/admin/comments/${id}/status`, null, { params: { status } })
  }
}