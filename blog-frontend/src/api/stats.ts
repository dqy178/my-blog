/**
 * 仪表盘统计的接口封装与类型定义。
 */
import request from '@/utils/request'

/** 统计概览出参 */
export interface StatsVO {
  /** 已发布文章数 */
  articleCount: number
  /** 分类总数 */
  categoryCount: number
  /** 标签总数 */
  tagCount: number
  /** 评论总数 */
  commentCount: number
  /** 总浏览量 */
  viewCount: number
  /** 访客数（当前复用总浏览量） */
  visitorCount: number
  /** 近 7 天浏览趋势 */
  viewTrend: Array<{ date: string; views: number }>
  /** 近 7 天发布趋势 */
  articleTrend: Array<{ date: string; count: number }>
}

export const statsApi = {
  /** 获取统计概览 */
  getOverview() {
    return request.get<StatsVO>('/admin/stats/overview')
  }
}