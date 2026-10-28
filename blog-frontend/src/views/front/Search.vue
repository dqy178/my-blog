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
            <el-button v-if="keyword" link @click="clearSearch">
              <component :is="Close" />
            </el-button>
          </template>
        </el-input>
      </div>

      <div v-if="keyword" class="search-results">
        <div v-if="articles.length > 0 || loading" class="result-stats">
          <span v-if="!loading">找到约 {{ total }} 条结果</span>
          <span v-else>搜索中...</span>
        </div>

        <div v-if="articles.length > 0" class="articles-list">
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

        <div v-else-if="!loading" class="empty-state">
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

      <div v-else class="search-hot">
        <h3 class="hot-title">热门搜索</h3>
        <div class="hot-tags">
          <span
            v-for="tag in hotTags"
            :key="tag.id"
            class="hot-tag"
            :style="{ backgroundColor: tag.color + '20', color: tag.color }"
            @click="searchByTag(tag.name)"
          >
            {{ tag.name }}
          </span>
        </div>
      </div>
    </div>
  </FrontLayout>
</template>

<script setup lang="ts">
/**
 * 前台搜索页。
 *
 * <p>搜索词来自路由 query（{@code ?q=}），因此搜索结果可以分享链接：
 * 点击热门标签会跳转到带 q 参数的地址，路由变化时自动重新搜索。</p>
 *
 * <p>输入框采用 500ms 防抖，避免每敲一个字就发一次请求。</p>
 */
import { logError } from '@/utils/logError'
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { articleApi, ArticleVO } from '@/api/article'
import { tagApi, TagVO } from '@/api/tag'
import Pagination from '@/components/Pagination.vue'
import FrontLayout from '@/layouts/FrontLayout.vue'
import { Search, Close } from '@element-plus/icons-vue'
import { safeHtml } from '@/utils/safeHtml'

const route = useRoute()
const router = useRouter()

/** 当前搜索词 */
const keyword = ref(route.query.q as string || '')
/** 热门标签（搜索框下方推荐） */
const hotTags = ref<TagVO[]>([])
/** 搜索结果列表 */
const articles = ref<ArticleVO[]>([])
/** 结果总数 */
const total = ref(0)
/** 当前页码 */
const pageNum = ref(1)
/** 每页条数 */
const pageSize = ref(10)
/** 搜索进行中 */
const loading = ref(false)
/** 防抖定时器句柄 */
let debounceTimer: ReturnType<typeof setTimeout> | null = null

/** 加载热门标签（取前 10 个作为推荐） */
async function fetchHotTags() {
  try {
    const res = await tagApi.getTagCloud()
    hotTags.value = res.slice(0, 10)
  } catch (error) {
    logError('获取热门标签失败:', error)
  }
}

/** 主动触发搜索：回到第 1 页 */
async function handleSearch() {
  pageNum.value = 1
  await searchArticles()
}

/**
 * 输入防抖：停止输入 500ms 后才真正发起搜索。
 *
 * <p>每次输入都会清除上一次的定时器，保证只有最后一次输入生效。</p>
 */
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

/**
 * 按当前关键词执行搜索。
 *
 * <p>固定带上 {@code status: 1}，只搜索已发布的文章。</p>
 */
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
    logError('搜索失败:', error)
  } finally {
    loading.value = false
  }
}

/**
 * 分页变化：更新页码/页大小后重新搜索并回到顶部。
 *
 * @param page 新页码
 * @param size 新的每页条数（可选）
 */
function handlePageChange(page: number, size?: number) {
    pageNum.value = page
    if (size && size !== pageSize.value) {
      pageSize.value = size
    }
    searchArticles()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

/** 清空搜索词与结果 */
function clearSearch() {
  keyword.value = ''
  articles.value = []
  total.value = 0
  router.replace({ path: '/search' })
}

/**
 * 按标签名搜索。
 *
 * <p>走路由跳转而不是直接请求：这样搜索条件会同步到地址栏，
 * 用户可以复制链接分享。</p>
 *
 * @param tagName 标签名
 */
function searchByTag(tagName: string) {
  router.push({ path: '/search', query: { q: tagName } })
}

/**
 * 格式化日期为「2026年10月」形式。
 *
 * @param dateStr 时间字符串
 */
function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

/**
 * 高亮搜索关键词。
 *
 * <p>先转义正则元字符避免关键词被当作正则语法，再用 {@code <mark>} 包裹命中片段。
 * 最后必须过 {@code safeHtml}——关键词来自用户输入，
 * 直接拼进 HTML 会有 XSS 风险。</p>
 *
 * @param text 原始文本
 * @returns 净化后的 HTML
 */
function highlight(text: string) {
  if (!keyword.value.trim() || !text) return safeHtml(text)
  const regex = new RegExp(`(${keyword.value.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return safeHtml(text.replace(regex, '<mark>$1</mark>'))
}

/** 初始化：加载热门标签，URL 带搜索词时直接执行搜索 */
onMounted(() => {
  fetchHotTags()
  if (keyword.value) {
    searchArticles()
  }
})

/**
 * 监听 URL 中的搜索词变化。
 *
 * <p>点击热门标签或清空搜索时触发，保证地址栏与搜索状态始终一致。</p>
 */
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