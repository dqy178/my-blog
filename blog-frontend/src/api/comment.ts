/**
 * 评论相关的接口封装与类型定义。
 *
 * <p>类型与后端 {@code com.blog.vo.CommentVO} / {@code com.blog.dto.CommentDTO} 一一对应。</p>
 */
import request from '@/utils/request'
import type { PageResult } from './types'

export type { PageResult } from './types'

/** 评论出参 */
export interface CommentVO {
  /** 评论 ID */
  id: number
  /** 所属文章 ID */
  articleId: number
  /** 父评论 ID。顶级评论为 null */
  parentId: number
  /** 评论者用户 ID。游客评论为 null */
  userId: number
  /** 展示昵称 */
  nickname: string
  /** 联系邮箱 */
  email: string
  /** 评论内容（已做 XSS 净化） */
  content: string
  /** 审核状态：0=待审核，1=通过，2=拒绝 */
  status: number
  /** 是否管理员回复：1=是（前端显示「博主」标识） */
  isAdmin: number
  /** 创建时间 */
  createTime: string
  /** 最后更新时间 */
  updateTime: string
  /** 子回复列表 */
  children: CommentVO[]
  /** 评论者昵称（预留给登录用户评论场景） */
  userNickname: string
  /** 评论者头像（预留给登录用户评论场景） */
  userAvatar: string
  /** 所属文章标题（后台评论列表使用） */
  articleTitle?: string
}

/** 评论入参 */
export interface CommentDTO {
  /** 主键 ID，新建时不需要 */
  id?: number
  /** 所属文章 ID */
  articleId: number
  /** 父评论 ID。undefined 表示开楼评论 */
  parentId?: number
  /** 评论内容 */
  content: string
  /** 昵称。游客必填 */
  nickname?: string
  /** 邮箱。游客必填 */
  email?: string
  /** 游客评论必填：验证码 ID，由 GET /api/auth/captcha 获取 */
  captchaId?: string
  /** 游客评论必填：验证码答案 */
  captcha?: string
}

/** 评论查询条件 */
export interface CommentQueryDTO {
  /** 页码，从 1 开始 */
  pageNum: number
  /** 每页条数，上限 100 */
  pageSize: number
  /** 按文章过滤 */
  articleId?: number
  /** 按审核状态过滤 */
  status?: number
  /** 排序字段 */
  sortField?: string
  /** 排序方向 asc/desc */
  sortOrder?: string
}

export const commentApi = {
  // 前台接口
  /** 获取文章下的评论树（仅已通过的评论） */
  getCommentsByArticleId(articleId: number) {
    return request.get<CommentVO[]>(`/front/comments/article/${articleId}`)
  },

  /** 分页查询评论 */
  getCommentPage(params: CommentQueryDTO) {
    return request.get<PageResult<CommentVO>>('/front/comments/page', params)
  },

  /** 提交评论（游客需带验证码） */
  createComment(data: CommentDTO) {
    return request.post<CommentVO>('/front/comments', data)
  },

  // 后台接口
  /** 后台分页查询评论（可按审核状态筛选） */
  getAdminCommentPage(params: CommentQueryDTO) {
    return request.get<PageResult<CommentVO>>('/admin/comments/page', params)
  },

  /** 后台获取文章下的评论树 */
  getAdminCommentsByArticleId(articleId: number) {
    return request.get<CommentVO[]>(`/admin/comments/article/${articleId}`)
  },

  /** 管理员回复评论 */
  adminReplyComment(id: number, data: CommentDTO) {
    return request.post<CommentVO>(`/admin/comments/${id}/reply`, data)
  },

  /** 删除评论（级联删除子回复） */
  deleteComment(id: number) {
    return request.delete(`/admin/comments/${id}`)
  },

  /** 批量删除评论 */
  batchDeleteComments(ids: number[]) {
    return request.deleteBatch('/admin/comments/batch', ids)
  },

  /** 修改审核状态：1=通过，2=拒绝 */
  updateCommentStatus(id: number, status: number) {
    return request.put(`/admin/comments/${id}/status`, null, { params: { status } })
  }
}