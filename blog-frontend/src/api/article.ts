import request from '@/utils/request'

export interface ArticleVO {
  id: number
  title: string
  summary: string
  content: string
  cover: string
  categoryId: number
  categoryName: string
  status: number
  isTop: number
  viewCount: number
  likeCount: number
  commentCount: number
  allowComment: number
  createTime: string
  updateTime: string
  publishTime: string
  tagNames: string[]
  tags: TagVO[]
}

export interface TagVO {
  id: number
  name: string
  color: string
  articleCount: number
  createTime: string
  updateTime: string
}

export interface ArticleDTO {
  id?: number
  title: string
  summary: string
  content: string
  cover: string
  categoryId: number
  status: number
  isTop: number
  allowComment: number
  tagIds: number[]
}

export interface ArticleQueryDTO {
  pageNum: number
  pageSize: number
  categoryId?: number
  tagId?: number
  keyword?: string
  status?: number
  isTop?: number
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

export const articleApi = {
  // 前台接口
  getArticlePage(params: ArticleQueryDTO) {
    return request.get<PageResult<ArticleVO>>('/front/articles/page', params)
  },

  getArticleDetail(id: number) {
    return request.get<ArticleVO>(`/front/articles/${id}`)
  },

  incrementView(id: number) {
    return request.post(`/front/articles/${id}/view`)
  },

  getHotArticles(limit = 10) {
    return request.get<ArticleVO[]>('/front/articles/hot', { limit })
  },

  getLatestArticles(limit = 10) {
    return request.get<ArticleVO[]>('/front/articles/latest', { limit })
  },

  getRelatedArticles(id: number, categoryId: number, limit = 5) {
    return request.get<ArticleVO[]>(`/front/articles/${id}/related`, { categoryId, limit })
  },

  getPrevArticle(id: number) {
    return request.get<ArticleVO | null>(`/front/articles/${id}/prev`)
  },

  getNextArticle(id: number) {
    return request.get<ArticleVO | null>(`/front/articles/${id}/next`)
  },

  getArchive() {
    return request.get<ArticleVO[]>('/front/articles/archive')
  },

  // 后台接口
  getAdminArticlePage(params: ArticleQueryDTO) {
    return request.get<PageResult<ArticleVO>>('/admin/articles/page', params)
  },

  getAdminArticleDetail(id: number) {
    return request.get<ArticleVO>(`/admin/articles/${id}`)
  },

  createArticle(data: ArticleDTO) {
    return request.post<ArticleVO>('/admin/articles', data)
  },

  updateArticle(id: number, data: ArticleDTO) {
    return request.put<ArticleVO>(`/admin/articles/${id}`, data)
  },

  deleteArticle(id: number) {
    return request.delete(`/admin/articles/${id}`)
  },

  batchDeleteArticles(ids: number[]) {
    return request.delete('/admin/articles/batch', { data: ids })
  },

  updateArticleStatus(id: number, status: number) {
    return request.put(`/admin/articles/${id}/status`, null, { params: { status } })
  },

  batchUpdateArticleStatus(ids: number[], status: number) {
    return request.put('/admin/articles/batch/status', null, { params: { data: ids, status } })
  },

  updateArticleTop(id: number, isTop: number) {
    return request.put(`/admin/articles/${id}/top`, null, { params: { isTop } })
  }
}