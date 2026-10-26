import request from '@/utils/request'

export interface StatsVO {
  articleCount: number
  categoryCount: number
  tagCount: number
  commentCount: number
  viewCount: number
  visitorCount: number
  viewTrend: Array<{ date: string; views: number }>
  articleTrend: Array<{ date: string; count: number }>
}

export const statsApi = {
  getOverview() {
    return request.get<StatsVO>('/admin/stats/overview')
  }
}