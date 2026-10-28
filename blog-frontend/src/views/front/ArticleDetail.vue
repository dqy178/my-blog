<template>
  <FrontLayout>
    <div class="article-detail-page">
      <article v-if="article" class="article-container">
        <header class="article-header">
          <div class="article-meta">
            <router-link :to="`/category/${article.categoryId}`" class="category-tag">
              {{ article.categoryName }}
            </router-link>
            <span class="publish-time">{{ formatDate(article.publishTime || article.createTime) }}</span>
          </div>
          
          <span class="field-label">文章标题</span>
          <div class="title-box">
            <h1 class="article-title">{{ article.title }}</h1>
          </div>
          
          <span v-if="article.summary" class="field-label">文章摘要</span>
          <div v-if="article.summary" class="summary-box">
            <p>{{ article.summary }}</p>
          </div>
          
          <span class="field-label">文章内容</span>
          <div class="content-box">
            <div class="article-content" v-html="renderedContent"></div>
          </div>
          
          <div v-if="article.tags && article.tags.length" class="article-tags">
            <router-link
              v-for="tag in article.tags"
              :key="tag.id"
              :to="`/tag/${tag.id}`"
              class="tag"
              :style="{ borderColor: tag.color, color: tag.color }"
            >
              {{ tag.name }}
            </router-link>
          </div>
        </header>

        <footer class="article-footer">
          <div class="article-stats">
            <span class="stat">
              <el-icon><View /></el-icon>
              {{ article.viewCount }} 阅读
            </span>
            <span class="stat">
              <el-icon><ChatDotRound /></el-icon>
              {{ article.commentCount }} 评论
            </span>
          </div>
          <div class="article-actions">
            <el-button type="primary" @click="copyLink">
              <el-icon class="mr-1"><Link /></el-icon>
              复制链接
            </el-button>
          </div>
        </footer>

        <div v-if="prevArticle || nextArticle" class="article-nav">
          <router-link
            v-if="prevArticle"
            :to="navLink(prevArticle)"
            class="nav-item prev"
          >
            <el-icon class="nav-icon"><ArrowLeft /></el-icon>
            <div class="nav-info">
              <span class="nav-label">上一篇</span>
              <span class="nav-title">{{ prevArticle.title }}</span>
            </div>
          </router-link>
          <router-link
            v-if="nextArticle"
            :to="navLink(nextArticle)"
            class="nav-item next"
          >
            <div class="nav-info">
              <span class="nav-label">下一篇</span>
              <span class="nav-title">{{ nextArticle.title }}</span>
            </div>
            <el-icon class="nav-icon"><ArrowRight /></el-icon>
          </router-link>
        </div>

        <CommentSection :article-id="article.id" @count-update="onCommentCountUpdate" />
      </article>

      <div v-else class="loading">
        <el-skeleton :rows="5" animated />
      </div>
    </div>
  </FrontLayout>
</template>

<script setup lang="ts">
/**
 * 前台文章详情页。
 *
 * <p>展示文章正文（Markdown 渲染）、标签、统计信息、上一篇/下一篇导航，
 * 以及评论区。路由参数变化时会重新拉取数据并重新计一次浏览量。</p>
 */
import { logError } from '@/utils/logError'
import { onMounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import markdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import { safeHtml } from '@/utils/safeHtml'
import {
  View,
  ChatDotRound,
  Link,
  ArrowLeft,
  ArrowRight
} from '@element-plus/icons-vue'
import { articleApi } from '@/api/article'
import FrontLayout from '@/layouts/FrontLayout.vue'
import CommentSection from '@/components/CommentSection.vue'
import type { ArticleVO } from '@/api/article'

const route = useRoute()
const router = useRouter()

/** 当前文章 */
const article = ref<ArticleVO | null>(null)
/** 上一篇（列表中更靠前的一篇） */
const prevArticle = ref<ArticleVO | null>(null)
/** 下一篇（列表中更靠后的一篇） */
const nextArticle = ref<ArticleVO | null>(null)
/** 净化后的正文 HTML */
const renderedContent = ref('')

/**
 * 当前的作用域上下文（从 URL 查询参数读取）。
 *
 * <p>标签页进入时 URL 形如 {@code /article/12?tag=3}，分类页形如
 * {@code /article/12?category=5}。作用域写在 URL 里而不是路由 state，
 * 是为了让刷新页面、复制链接分享都能保持正确的上下篇范围。</p>
 */
const navScope = computed(() => {
  const tagId = route.query.tag ? Number(route.query.tag) : undefined
  const categoryId = route.query.category ? Number(route.query.category) : undefined
  if (!tagId && !categoryId) return undefined
  return { tagId, categoryId }
})

/**
 * 取得当前作用域，供请求参数使用。
 *
 * <p>无作用域时返回 undefined，请求便不带这两个参数，后端按全站范围处理。</p>
 */
function currentScope() {
  return navScope.value
}

/**
 * 上下篇链接的目标地址。
 *
 * <p>必须带上作用域，否则在标签页内连点「下一篇」会退回全站顺序，
 * 上下文就断了。</p>
 */
function navLink(target: ArticleVO) {
  return {
    path: `/article/${target.id}`,
    query: navScope.value
      ? { ...(navScope.value.tagId ? { tag: navScope.value.tagId } : {}),
          ...(navScope.value.categoryId ? { category: navScope.value.categoryId } : {}) }
      : {}
  }
}

/**
 * Markdown 渲染器。
 *
 * <p>{@code html: true} 允许正文内嵌 HTML，因此渲染结果必须再经 safeHtml 净化。</p>
 */
const md = markdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight: (str, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
      } catch (__) { /* ignore */ }
    }
    return ''
  }
})

/**
 * 拉取文章详情与上下篇。
 *
 * <p>三个请求并发执行；上下篇的失败（没有上一篇/下一篇属正常情况）被单独 catch，
 * 不应因此让整页跳 404。</p>
 *
 * <p>上下篇会带上 {@link currentScope} 作用域：从标签页/分类页进入时，
 * 切换范围限定在对应集合内；直接输 URL 或从首页进入时为空，即全站范围。</p>
 */
async function fetchArticle() {
  try {
    const id = Number(route.params.id)
    const scope = currentScope()
    const [articleRes, prevRes, nextRes] = await Promise.all([
      articleApi.getArticleDetail(id),
      articleApi.getPrevArticle(id, scope).catch(() => null),
      articleApi.getNextArticle(id, scope).catch(() => null)
    ])
    article.value = articleRes
    prevArticle.value = prevRes || null
    nextArticle.value = nextRes || null
    renderContent()
  } catch (error) {
    logError('获取文章详情失败:', error)
    router.push('/404')
  }
}

/**
 * 渲染并净化正文 HTML。
 */
function renderContent() {
  if (!article.value) return
  const html = md.render(article.value.content)
  const cleanHtml = safeHtml(html)
  renderedContent.value = cleanHtml
}

/**
 * 格式化日期为中文长格式。
 *
 * @param dateStr 时间字符串
 * @returns 格式化后的日期；入参为空或非法时返回空串
 */
function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return ''
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  })
}

/**
 * 接收评论区回传的最新评论数，同步到页头统计。
 *
 * @param count 当前评论数（含回复）
 */
function onCommentCountUpdate(count: number) {
  if (article.value) {
    article.value.commentCount = count
  }
}

/**
 * 复制当前页面链接到剪贴板。
 */
function copyLink() {
  navigator.clipboard.writeText(window.location.href).then(() => {
    ElMessage.success('链接已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

onMounted(async () => {
  await fetchArticle()
  // 只有从其他页面进入详情页时才计数（本次会话内同一文章只计一次）
  const viewedKey = `article_viewed_${route.params.id}`
  if (!sessionStorage.getItem(viewedKey)) {
    try {
      await articleApi.incrementView(Number(route.params.id))
      sessionStorage.setItem(viewedKey, '1')
      // 更新显示的浏览量
      if (article.value) {
        article.value.viewCount = (article.value.viewCount || 0) + 1
      }
    } catch (e) {
      // 忽略统计失败
    }
  }
})

/**
 * 路由参数变化（点击上下篇切换）时重新加载内容，并按同样的去重规则计浏览量。
 */
watch(() => route.params.id, async (newId) => {
  if (newId) {
    await fetchArticle()
    const viewedKey = `article_viewed_${newId}`
    if (!sessionStorage.getItem(viewedKey)) {
      try {
        await articleApi.incrementView(Number(newId))
        sessionStorage.setItem(viewedKey, '1')
        if (article.value) {
          article.value.viewCount = (article.value.viewCount || 0) + 1
        }
      } catch (e) {
        // 忽略统计失败
      }
    }
  }
})
</script>

<style scoped>
.article-detail-page {
  width: 100%;
  margin: 0 auto;
  padding: 0 20px;
  box-sizing: border-box;
}

.article-container {
  background: rgba(255, 255, 255, 0.3);
  border: 1px solid #ebeef5;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  overflow: hidden;
}

.article-header {
  padding: 40px 40px 24px;
  border-bottom: 1px solid #ebeef5;
}

.article-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.category-tag {
  padding: 4px 12px;
  background: #f0f9ff;
  color: #409eff;
  border-radius: 4px;
  font-size: 20px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.3s;
}

.category-tag:hover {
  background: #d9ecff;
}

.publish-time {
  font-size: 20px;
  color: #000;
}

.field-label {
  font-weight: 700;
  font-size: 20px;
  display: block;
  margin-bottom: 4px;
}
.title-box, .summary-box, .content-box {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 40px;
  margin: 8px 0;
  background: rgba(255, 255, 255, 0.3);
  font-size: 18px;
}
.article-title {
  font-size: 20px;
  font-weight: 400;
  margin: 0;
}

.article-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  padding: 4px 12px;
  border: 1px solid;
  border-radius: 20px;
  font-size: 13px;
  text-decoration: none;
  transition: all 0.3s;
}

.tag:hover {
  background: var(--tag-bg, #f5f7fa);
}

.article-cover {
  margin: 0 40px 32px;
  border-radius: 8px;
  overflow: hidden;
}

.article-cover img {
  width: 100%;
  height: auto;
  max-height: 200px;
  object-fit: cover;
  display: block;
}

.article-content :deep(img) {
  max-width: 100%;
  max-height: 300px;
  object-fit: contain;
}

.article-content {
  padding: 0 0 32px;
}
.article-content h1,
.article-content h2,
.article-content h3,
.article-content h4,
.article-content h5,
.article-content h6 {
  margin-top: 32px;
  margin-bottom: 16px;
  font-weight: 600;
  color: #303133;
}

.article-content h1 { font-size: 28px; }
.article-content h2 { font-size: 24px; border-bottom: 2px solid #409eff; padding-bottom: 8px; }
.article-content h3 { font-size: 20px; }
.article-content h4 { font-size: 18px; }
.article-content h5 { font-size: 16px; }
.article-content h6 { font-size: 15px; }

.article-content p {
  margin-bottom: 16px;
}

.article-content a {
  color: #409eff;
  text-decoration: underline;
}

.article-content code {
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.9em;
}

.article-content pre {
  background: #2d2d2d;
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
  margin: 24px 0;
}

.article-content pre code {
  background: transparent;
  padding: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #f8f8f2;
}

.article-content blockquote {
  border-left: 4px solid #409eff;
  padding-left: 16px;
  margin: 24px 0;
  color: #909399;
  font-style: italic;
}

.article-content ul,
.article-content ol {
  margin: 16px 0;
  padding-left: 24px;
}

.article-content li {
  margin-bottom: 8px;
}

.article-content img {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 16px 0;
}

.article-content table {
  width: 100%;
  border-collapse: collapse;
  margin: 24px 0;
}

.article-content th,
.article-content td {
  border: 1px solid #ebeef5;
  padding: 12px;
  text-align: left;
}

.article-content th {
  background: #f5f7fa;
  font-weight: 600;
}

.article-content hr {
  border: none;
  border-top: 1px solid #ebeef5;
  margin: 32px 0;
}

.article-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 40px;
  border-top: 1px solid #ebeef5;
  background: rgba(255, 255, 255, 0.3);
}

.article-stats {
  display: flex;
  gap: 34px;
}

.stat {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 20px;
  color: #909399;
}
.stat svg {
  width: 1em;
  height: 1em;
}

.article-nav {
  display: flex;
  padding: 24px 40px;
  border-top: 1px solid #ebeef5;
  gap: 24px;
}

.nav-item {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 30px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  text-decoration: none;
  color: #303133;
  transition: all 0.3s;
}

.nav-item:hover {
  background: #f0f9ff;
  color: #409eff;
}

.nav-item.prev {
  flex-direction: row;
}

.nav-item.next {
  flex-direction: row;
  justify-content: flex-end;
}

.nav-icon {
  width: 16px;
  height: 16px;
  color: #909399;
  flex-shrink: 0;
}

.nav-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-label {
  font-size: 18px;
  color: #000;
}

.nav-title {
  font-size: 18px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.loading {
  padding: 40px;
}

@media (max-width: 768px) {
  .article-header,
  .article-content,
  .article-footer,
  .article-nav {
    padding-left: 20px;
    padding-right: 20px;
  }

  .article-title {
    font-size: 24px;
  }

  .article-cover {
    margin: 0 20px 24px;
  }

  .article-nav {
    flex-direction: column;
  }

  .nav-item.next {
    flex-direction: column;
  }

  .article-stats {
    flex-wrap: wrap;
    gap: 16px;
  }
}
</style>