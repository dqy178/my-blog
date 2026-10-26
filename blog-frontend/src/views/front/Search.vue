<template>
  <FrontLayout>
    <div class="search-page">
      <div class="search-header">
        <h1 class="page-title">搜索</h1>
        <el-input
          v-model="keyword"
          placeholder="输入关键词搜索文章..."
          class="search-input"
          @keydown.enter="handleSearch"
          @input="debouncedSearch"
        >
          <template #prefix>
            <component :is="Search" class="search-icon" />
          </template>
          <template #suffix>
            <el-button v-if="keyword" type="text" @click="clearSearch">
              <component :is="Close" />
            </el-button>
          </template>
        </el-input>
      </div>

      <div class="search-results" v-if="keyword">
        <div class="result-stats" v-if="articles.length > 0 || loading">
          <span v-if="!loading">找到约 {{ total }} 条结果</span>
          <span v-else>搜索中...</span>
        </div>

        <div class="articles-list" v-if="articles.length > 0">
          <div
            v-for="article in articles"
            :key="article.id"
            class="article-item"
          >
            <router-link :to="`/article/${article.id}`" class="article-link">
              <h3 class="article-title" v-html="highlight(article.title)"></h3>
              <p class="article-summary" v-html="highlight(article.summary || '')"></p>
              <div class="article-meta">
                <span>{{ formatDate(article.publishTime || article.createTime) }}</span>
                <span v-if="article.categoryName">{{ article.categoryName }}</span>
                <span>{{ article.viewCount }} 阅读</span>
              </div>
            </router-link>
          </div>
        </div>

        <div class="empty-state" v-else-if="!loading">
          <component :is="Search" class="empty-icon" />
          <p>未找到相关文章</p>
          <p class="empty-hint">尝试使用不同的关键词</p>
        </div>

        <Pagination
          v-if="total > pageSize && !loading"
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          @page-change="handlePageChange"
        />
      </div>

      <div class="search-hot" v-else>
        <h3 class="hot-title">热门搜索</h3>
        <div class="hot-tags">
          <span
            v-for="tag in hotTags"
            :key="tag.id"
            class="hot-tag"
            @click="searchByTag(tag.name)"
            :style="{ backgroundColor: tag.color + '20', color: tag.color }"
          >
            {{ tag.name }}
          </span>
        </div>
      </div>
    </div>
  </FrontLayout>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { articleApi, ArticleVO } from '@/api/article'
import { tagApi, TagVO } from '@/api/tag'
import Pagination from '@/components/Pagination.vue'
import FrontLayout from '@/layouts/FrontLayout.vue'
import { Search, Close } from '@element-plus/icons-vue'
import { safeHtml } from '@/utils/safeHtml'

const route = useRoute()
const router = useRouter()

const keyword = ref(route.query.q as string || '')
const hotTags = ref<TagVO[]>([])
const articles = ref<ArticleVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
let debounceTimer: ReturnType<typeof setTimeout> | null = null

async function fetchHotTags() {
  try {
    const res = await tagApi.getTagCloud()
    hotTags.value = res.slice(0, 10)
  } catch (error) {
    console.error('获取热门标签失败:', error)
  }
}

async function handleSearch() {
  pageNum.value = 1
  await searchArticles()
}

function debouncedSearch() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    if (keyword.value.trim()) {
      pageNum.value = 1
      searchArticles()
    } else {
      articles.value = []
      total.value = 0
    }
  }, 500)
}

async function searchArticles() {
  if (!keyword.value.trim()) return
  loading.value = true
  try {
    const res = await articleApi.getArticlePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim(),
      status: 1
    })
    articles.value = res.list
    total.value = res.total
  } catch (error) {
    console.error('搜索失败:', error)
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  pageNum.value = page
  searchArticles()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function clearSearch() {
  keyword.value = ''
  articles.value = []
  total.value = 0
  router.replace({ path: '/search' })
}

function searchByTag(tagName: string) {
  router.push({ path: '/search', query: { q: tagName } })
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

function highlight(text: string) {
  if (!keyword.value.trim() || !text) return safeHtml(text)
  const regex = new RegExp(`(${keyword.value.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return safeHtml(text.replace(regex, '<mark>$1</mark>'))
}

onMounted(() => {
  fetchHotTags()
  if (keyword.value) {
    searchArticles()
  }
})

watch(() => route.query.q, (newVal) => {
  keyword.value = newVal as string || ''
  if (keyword.value) {
    searchArticles()
  } else {
    articles.value = []
    total.value = 0
  }
})
</script>

<style scoped>
.search-page {
  width: 100%;
  margin: 0 auto;
  padding: 0 20px;
  box-sizing: border-box;
}

.search-header {
  margin-bottom: 32px;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 20px;
}

.search-input {
  width: 100%;
}

.search-input :deep(.el-input__inner) {
  font-size: 16px;
  padding: 16px 20px;
  border-radius: 50px;
  border: 2px solid #ebeef5;
  transition: all 0.3s;
}

.search-input :deep(.el-input__inner):focus {
  border-color: #409eff;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.1);
}

.search-icon {
  font-size: 18px;
  color: #909399;
}

.search-results {
  margin-top: 24px;
}

.result-stats {
  font-size: 14px;
  color: #909399;
  margin-bottom: 20px;
}

.articles-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.article-item {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  transition: all 0.3s;
}

.article-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.article-link {
  display: block;
  padding: 24px;
  text-decoration: none;
  color: inherit;
}

.article-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  line-height: 1.4;
  transition: color 0.3s;
}

.article-link:hover .article-title {
  color: #409eff;
}

.article-title mark {
  background: #fffbe6;
  color: #e6a23c;
  padding: 2px 4px;
  border-radius: 2px;
}

.article-summary {
  font-size: 14px;
  color: #606266;
  line-height: 1.7;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-summary mark {
  background: #fffbe6;
  color: #e6a23c;
  padding: 2px 4px;
  border-radius: 2px;
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

.empty-hint {
  font-size: 14px;
  margin-top: 8px;
}

.search-hot {
  margin-top: 48px;
}

.hot-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}

.hot-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hot-tag {
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid;
}

.hot-tag:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

@media (max-width: 768px) {
  .page-title {
    font-size: 24px;
  }

  .search-input :deep(.el-input__inner) {
    padding: 14px 16px;
    font-size: 15px;
  }
}
</style>