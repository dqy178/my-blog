<template>
  <div class="dashboard-page">
    <el-row :gutter="20" class="stats-cards">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-info">
              <p class="stat-label">文章总数</p>
              <h3 class="stat-value">{{ stats.articleCount }}</h3>
            </div>
            <div class="stat-icon-wrapper" style="background: #e6f7ff; color: #409eff;">
              <component :is="Document" />
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-info">
              <p class="stat-label">分类总数</p>
              <h3 class="stat-value">{{ stats.categoryCount }}</h3>
            </div>
            <div class="stat-icon-wrapper" style="background: #f0f9eb; color: #67c23a;">
              <component :is="Folder" />
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-info">
              <p class="stat-label">标签总数</p>
              <h3 class="stat-value">{{ stats.tagCount }}</h3>
            </div>
            <div class="stat-icon-wrapper" style="background: #fdf6ec; color: #e6a23c;">
              <component :is="PriceTag" />
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-info">
              <p class="stat-label">总浏览量</p>
              <h3 class="stat-value">{{ formatNumber(stats.viewCount) }}</h3>
            </div>
            <div class="stat-icon-wrapper" style="background: #fef0f0; color: #f56c6c;">
              <component :is="View" />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="charts-row">
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>浏览趋势 (近7天)</span>
            </div>
          </template>
          <div class="chart-container" ref="viewChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>文章发布趋势 (近7天)</span>
            </div>
          </template>
          <div class="chart-container" ref="articleChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="tables-row">
      <el-col :xs="24" :lg="12">
        <el-card class="table-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>最新文章</span>
              <router-link to="/admin/articles" class="view-all">查看全部</router-link>
            </div>
          </template>
          <el-table :data="latestArticles" border stripe size="small" style="width: 100%">
            <el-table-column prop="title" label="标题" show-overflow-tooltip>
              <template #default="scope">
                <router-link :to="`/admin/articles/edit/${scope.row.id}`" class="table-link">
                  {{ scope.row.title }}
                </router-link>
              </template>
            </el-table-column>
            <el-table-column prop="categoryName" label="分类" width="120" />
            <el-table-column prop="viewCount" label="阅读" width="80" />
            <el-table-column label="状态" width="80">
              <template #default="scope">
                <el-tag :type="getStatusType(scope.row.status)" size="small">
                  {{ scope.row.status === 1 ? '已发布' : '草稿' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160">
              <template #default="scope">
                {{ formatDate(scope.row.createTime) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="table-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>最新评论</span>
              <router-link to="/admin/comments" class="view-all">查看全部</router-link>
            </div>
          </template>
          <el-table :data="latestComments" border stripe size="small" style="width: 100%">
            <el-table-column prop="content" label="内容" show-overflow-tooltip min-width="200" />
            <el-table-column prop="nickname" label="作者" width="100" />
            <el-table-column prop="articleTitle" label="文章" min-width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="80">
              <template #default="scope">
                <el-tag :type="getCommentStatusType(scope.row.status)" size="small">
                  {{ scope.row.status === 1 ? '已通过' : scope.row.status === 0 ? '待审核' : '已拒绝' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" width="140">
              <template #default="scope">
                {{ formatDate(scope.row.createTime) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, nextTick } from 'vue'
import { statsApi, StatsVO } from '@/api/stats'
import { articleApi, ArticleVO } from '@/api/article'
import { commentApi, CommentVO } from '@/api/comment'
import {
  Document,
  Folder,
  PriceTag,
  View
} from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  CanvasRenderer
])

const stats = ref<StatsVO>({
  articleCount: 0,
  categoryCount: 0,
  tagCount: 0,
  commentCount: 0,
  viewCount: 0,
  visitorCount: 0,
  viewTrend: [],
  articleTrend: []
})

const latestArticles = ref<ArticleVO[]>([])
const latestComments = ref<CommentVO[]>([])

const viewChartRef = ref<HTMLElement>()
const articleChartRef = ref<HTMLElement>()
const articleTitleCache = new Map<number, string>()

async function fetchData() {
  try {
    const [statsRes, articlesRes, commentsRes] = await Promise.all([
      statsApi.getOverview(),
      articleApi.getAdminArticlePage({ pageNum: 1, pageSize: 5, sortField: 'createTime', sortOrder: 'desc' }),
      commentApi.getAdminCommentPage({ pageNum: 1, pageSize: 5 })
    ])
    stats.value = statsRes
    latestArticles.value = articlesRes.list
    latestComments.value = commentsRes.list
    // 批量获取文章标题
    const articleIds = [...new Set(latestComments.value.map(c => c.articleId))]
    await Promise.allSettled(articleIds.map(async (articleId) => {
      if (articleTitleCache.has(articleId)) {
        const title = articleTitleCache.get(articleId)
        for (const comment of latestComments.value) {
          if (comment.articleId === articleId) {
            comment.articleTitle = title
          }
        }
        return
      }
      try {
        const article = await articleApi.getArticleDetail(articleId)
        articleTitleCache.set(articleId, article.title)
        for (const comment of latestComments.value) {
          if (comment.articleId === articleId) {
            comment.articleTitle = article.title
          }
        }
      } catch (e) {}
    }))
    nextTick(() => initCharts())
  } catch (error) {
    console.error('获取仪表盘数据失败:', error)
  }
}

function initCharts() {
  if (viewChartRef.value) {
    const viewChart = echarts.init(viewChartRef.value)
    viewChart.setOption({
      grid: { left: 40, right: 20, top: 20, bottom: 40 },
      xAxis: {
        type: 'category',
        data: stats.value.viewTrend.map(t => t.date),
        axisLine: { lineStyle: { color: '#ebeef5' } },
        axisTick: { show: false },
        axisLabel: { color: '#909399', fontSize: 12 }
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#909399', fontSize: 12 },
        splitLine: { lineStyle: { color: '#f5f7fa' } }
      },
      series: [{
        name: '浏览量',
        type: 'line',
        smooth: true,
        data: stats.value.viewTrend.map(t => t.views),
        lineStyle: { color: '#409eff', width: 2 },
        itemStyle: { color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0)' }
          ])
        },
        symbol: 'circle',
        symbolSize: 6
      }]
    }, true)
    window.addEventListener('resize', () => viewChart.resize())
  }

  if (articleChartRef.value) {
    const articleChart = echarts.init(articleChartRef.value)
    articleChart.setOption({
      grid: { left: 40, right: 20, top: 20, bottom: 40 },
      xAxis: {
        type: 'category',
        data: stats.value.articleTrend.map(t => t.date),
        axisLine: { lineStyle: { color: '#ebeef5' } },
        axisTick: { show: false },
        axisLabel: { color: '#909399', fontSize: 12 }
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#909399', fontSize: 12 },
        splitLine: { lineStyle: { color: '#f5f7fa' } }
      },
      series: [{
        name: '发布数',
        type: 'line',
        smooth: true,
        data: stats.value.articleTrend.map(t => t.count),
        lineStyle: { color: '#67c23a', width: 2 },
        itemStyle: { color: '#67c23a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(103, 194, 58, 0.3)' },
            { offset: 1, color: 'rgba(103, 194, 58, 0)' }
          ])
        },
        symbol: 'circle',
        symbolSize: 6
      }]
    }, true)
    window.addEventListener('resize', () => articleChart.resize())
  }
}

function getStatusType(status: number) {
  return status === 1 ? 'success' : 'info'
}

function getCommentStatusType(status: number) {
  return status === 1 ? 'success' : status === 0 ? 'warning' : 'danger'
}

function formatNumber(num: number) {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toString()
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

let pollTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  fetchData()
  pollTimer = setInterval(() => {
    fetchData()
  }, 10000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.dashboard-page {
  padding: 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.stats-cards {
  margin-bottom: 24px;
  margin-top: 6px;
}

.stat-card {
  height: 100%;
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.stat-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px;
}

.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin: 0 0 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  margin: 0;
}

.stat-icon-wrapper {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.charts-row {
  margin-bottom: 24px;
}

.chart-card {
  height: 100%;
  border-radius: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.card-header span {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.view-all {
  font-size: 12px;
  color: #409eff;
  text-decoration: none;
}

.view-all:hover {
  color: #66b1ff;
}

.table-card {
  height: 100%;
  border-radius: 12px;
}

.table-card :deep(.el-table) {
  font-size: 13px;
}

.table-card :deep(.el-table__header-wrapper th) {
  background: #fafafa;
  color: #606266;
  font-weight: 600;
}

.table-link {
  color: #303133;
  text-decoration: none;
  transition: color 0.3s;
}

.table-link:hover {
  color: #409eff;
}

.chart-container {
  width: 100%;
  height: 100%;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>