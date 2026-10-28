/**
 * 文章相关的接口封装与类型定义。
 *
 * <p>类型与后端 {@code com.blog.vo.ArticleVO} / {@code com.blog.dto.ArticleDTO} 一一对应。</p>
 */
import request from '@/utils/request'
import type { TagVO } from './tag'
import type { PageResult } from './types'

// 统一从 tag/types 复用，避免多处重复定义
export type { TagVO } from './tag'
export type { PageResult } from './types'

/** 文章出参 */
export interface ArticleVO {
  /** 文章 ID */
  id: number
  /** 标题 */
  title: string
  /** 摘要 */
  summary: string
  /** 正文（Markdown 原文） */
  content: string
  /** 封面图 URL */
  cover: string
  /** 所属分类 ID */
  categoryId: number
  /** 所属分类名称 */
  categoryName: string
  /** 状态：0=草稿，1=发布，2=回收站 */
  status: number
  /** 是否置顶：1=是 */
  isTop: number
  /** 浏览量 */
  viewCount: number
  /** 已通过的评论数（含回复） */
  commentCount: number
  /** 是否允许评论：1=允许 */
  allowComment: number
  /** 创建时间 */
  createTime: string
  /** 最后更新时间 */
  updateTime: string
  /** 发布时间。下架后为 null */
  publishTime: string
  /** 标签名称列表 */
  tagNames: string[]
  /** 标签完整信息列表 */
  tags: TagVO[]
}

/** 文章入参 */
export interface ArticleDTO {
  /** 主键 ID，新建时不需要 */
  id?: number
  /** 标题 */
  title: string
  /** 摘要 */
  summary: string
  /** 正文（Markdown 原文） */
  content: string
  /** 封面图 URL */
  cover: string
  /** 所属分类 ID。表单初始为空，提交前由表单规则校验必填 */
  categoryId?: number
  /** 状态：0=草稿，1=发布，2=回收站 */
  status: number
  /** 是否置顶：1=是，0=否 */
  isTop: number
  /** 是否允许评论：1=允许，0=禁止 */
  allowComment: number
  /** 标签 ID 列表。传空数组表示清空所有标签 */
  tagIds: number[]
}

/** 文章查询条件 */
export interface ArticleQueryDTO {
  /** 页码，从 1 开始 */
  pageNum: number
  /** 每页条数，上限 100 */
  pageSize: number
  /** 按分类过滤 */
  categoryId?: number
  /** 按标签过滤 */
  tagId?: number
  /** 标题关键词，模糊匹配 */
  keyword?: string
  /** 按状态过滤 */
  status?: number
  /** 按置顶状态过滤 */
  isTop?: number
  /** 排序字段 */
  sortField?: string
  /** 排序方向 asc/desc */
  sortOrder?: string
}

/**
 * 上下篇查询的作用域。
 *
 * <p>从标签页 / 分类页进入文章时带上，详情页的「上一篇 / 下一篇」
 * 就会在该集合内切换，而不是跳到全站不相干标签的内容。</p>
 */
export interface ArticleNavScope {
  /** 限定在某个标签下的文章中查找 */
  tagId?: number
  /** 限定在某个分类下的文章中查找 */
  categoryId?: number
}

export const articleApi = {
  // 前台接口
  /** 分页查询已发布文章 */
  getArticlePage(params: ArticleQueryDTO) {
    return request.get<PageResult<ArticleVO>>('/front/articles/page', params)
  },

  /** 获取文章详情 */
  getArticleDetail(id: number) {
    return request.get<ArticleVO>(`/front/articles/${id}`)
  },

  /** 记录一次浏览 */
  incrementView(id: number) {
    return request.post(`/front/articles/${id}/view`)
  },

  /** 获取热门文章 */
  getHotArticles(limit = 10) {
    return request.get<ArticleVO[]>('/front/articles/hot', { limit })
  },

  /** 获取最新文章 */
  getLatestArticles(limit = 10) {
    return request.get<ArticleVO[]>('/front/articles/latest', { limit })
  },

  /** 获取相关推荐文章（同分类，排除当前文章） */
  getRelatedArticles(id: number, categoryId: number, limit = 5) {
    return request.get<ArticleVO[]>(`/front/articles/${id}/related`, { categoryId, limit })
  },

  /**
   * 获取上一篇，没有时返回 null。
   *
   * @param id    文章 ID
   * @param scope 可选作用域（标签/分类）；不传则为全站范围
   */
  getPrevArticle(id: number, scope?: ArticleNavScope) {
    return request.get<ArticleVO | null>(`/front/articles/${id}/prev`, scope)
  },

  /**
   * 获取下一篇，没有时返回 null。
   *
   * @param id    文章 ID
   * @param scope 可选作用域（标签/分类）；不传则为全站范围
   */
  getNextArticle(id: number, scope?: ArticleNavScope) {
    return request.get<ArticleVO | null>(`/front/articles/${id}/next`, scope)
  },

  /** 获取归档数据（全部已发布文章） */
  getArchive() {
    return request.get<ArticleVO[]>('/front/articles/archive')
  },

  // 后台接口
  /** 后台分页查询文章（不过滤状态） */
  getAdminArticlePage(params: ArticleQueryDTO) {
    return request.get<PageResult<ArticleVO>>('/admin/articles/page', params)
  },

  /** 后台获取文章详情（含草稿） */
  getAdminArticleDetail(id: number) {
    return request.get<ArticleVO>(`/admin/articles/${id}`)
  },

  /** 新建文章 */
  createArticle(data: ArticleDTO) {
    return request.post<ArticleVO>('/admin/articles', data)
  },

  /** 编辑文章 */
  updateArticle(id: number, data: ArticleDTO) {
    return request.put<ArticleVO>(`/admin/articles/${id}`, data)
  },

  /** 删除文章 */
  deleteArticle(id: number) {
    return request.delete(`/admin/articles/${id}`)
  },

  /** 批量删除文章 */
  batchDeleteArticles(ids: number[]) {
    return request.deleteBatch('/admin/articles/batch', ids)
  },

  /** 修改状态：0=草稿，1=发布，2=回收站 */
  updateArticleStatus(id: number, status: number) {
    return request.put(`/admin/articles/${id}/status`, null, { params: { status } })
  },

  /** 批量修改状态 */
  batchUpdateArticleStatus(ids: number[], status: number) {
    return request.put('/admin/articles/batch/status', null, { params: { data: ids, status } })
  },

  /** 设置/取消置顶 */
  updateArticleTop(id: number, isTop: number) {
    return request.put(`/admin/articles/${id}/top`, null, { params: { isTop } })
  }
}