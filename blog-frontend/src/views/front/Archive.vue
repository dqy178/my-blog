<template>
  <FrontLayout>
    <div class="archive-page">
      <div class="page-header">
        <h1 class="page-title">文章归档</h1>
        <p class="page-description">按时间线浏览所有文章</p>
      </div>

      <div class="archive-timeline" v-if="Object.keys(archives).length > 0">
        <div
          v-for="(group, year) in archives"
          :key="year"
          class="archive-year"
        >
          <div class="year-header">
            <span class="year-label">{{ year }}</span>
            <span class="year-count">{{ group.length }} 篇</span>
          </div>
          <div class="year-line"></div>
          <div class="articles-list">
            <div
              v-for="article in group"
              :key="article.id"
              class="article-item"
            >
              <div class="article-date">{{ formatDate(article.publishTime || article.createTime) }}</div>
              <router-link :to="`/article/${article.id}`" class="article-link">
                <span class="article-title">{{ article.title }}</span>
                <span class="article-meta">
                  <span v-if="article.categoryName">{{ article.categoryName }}</span>
                  <span>{{ article.viewCount }} 阅读</span>
                  <span>{{ article.commentCount }} 评论</span>
                </span>
              </router-link>
            </div>
          </div>
        </div>
      </div>

      <div class="empty-state" v-else>
        <component :is="FolderOpened" class="empty-icon" />
        <p>暂无文章</p>
      </div>
    </div>
  </FrontLayout>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { articleApi, ArticleVO } from '@/api/article'
import FrontLayout from '@/layouts/FrontLayout.vue'
import { FolderOpened } from '@element-plus/icons-vue'

const archives = ref<Record<string, ArticleVO[]>>({})

async function fetchArchive() {
  try {
    const res = await articleApi.getArchive()
    const grouped: Record<string, ArticleVO[]> = {}

    res.forEach(article => {
      const date = new Date(article.publishTime || article.createTime)
      const year = date.getFullYear().toString()
      if (!grouped[year]) {
        grouped[year] = []
      }
      grouped[year].push(article)
    })

    archives.value = grouped
  } catch (error) {
    console.error('获取归档失败:', error)
  }
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric'
  })
}

onMounted(() => {
  fetchArchive()
})
</script>

<style scoped>
.archive-page {
  width: 100%;
  margin: 0 auto;
  padding: 0 20px;
  box-sizing: border-box;
}

.page-header {
  text-align: center;
  padding: 40px 20px;
  margin-bottom: 32px;
  background: linear-gradient(135deg, #e6a23c 0%, #f0c86a 100%);
  border-radius: 12px;
  color: #fff;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 12px;
}

.page-description {
  font-size: 16px;
  opacity: 0.9;
}

.archive-timeline {
  position: relative;
}

.archive-year {
  margin-bottom: 40px;
}

.year-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.year-label {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}

.year-count {
  font-size: 14px;
  color: #909399;
  background: #f5f7fa;
  padding: 4px 12px;
  border-radius: 12px;
}

.year-line {
  position: absolute;
  left: 12px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #ebeef5;
  z-index: 0;
}

.articles-list {
  position: relative;
  z-index: 1;
}

.article-item {
  display: flex;
  gap: 16px;
  padding: 16px 0 16px 32px;
  position: relative;
  border-radius: 8px;
  transition: background 0.3s;
}

.article-item::before {
  content: '';
  position: absolute;
  left: 4px;
  top: 24px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid #409eff;
  box-shadow: 0 0 0 4px #fff;
  z-index: 2;
}

.article-item:hover {
  background: #fafafa;
}

.article-date {
  font-size: 13px;
  color: #909399;
  min-width: 70px;
  text-align: right;
  padding-top: 4px;
}

.article-link {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  text-decoration: none;
  color: inherit;
}

.article-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  transition: color 0.3s;
}

.article-link:hover .article-title {
  color: #409eff;
}

.article-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  text-align: center;
  color: #909399;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.5;
}

@media (max-width: 768px) {
  .page-title {
    font-size: 24px;
  }

  .article-item {
    padding-left: 28px;
  }

  .article-item::before {
    left: 2px;
    width: 12px;
    height: 12px;
  }

  .year-line {
    left: 8px;
  }
}
</style>