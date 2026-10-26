<template>
  <FrontLayout>
    <div class="article-detail-page">
      <article class="article-container" v-if="article">
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
          
          <span class="field-label" v-if="article.summary">文章摘要</span>
          <div class="summary-box" v-if="article.summary">
            <p>{{ article.summary }}</p>
          </div>
          
          <span class="field-label">文章内容</span>
          <div class="content-box">
            <div class="article-content" v-html="renderedContent"></div>
          </div>
          
          <div class="article-tags" v-if="article.tags && article.tags.length">
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

        <div class="article-nav" v-if="prevArticle || nextArticle">
          <router-link
            v-if="prevArticle"
            :to="`/article/${prevArticle.id}`"
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
            :to="`/article/${nextArticle.id}`"
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

      <div class="loading" v-else>
        <el-skeleton :rows="5" animated />
      </div>
    </div>
  </FrontLayout>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import markdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import DOMPurify from 'dompurify'
import {
  View,
  ChatDotRound,
  StarFilled,
  Link,
  ArrowLeft,
  ArrowRight
} from '@element-plus/icons-vue'
import { articleApi } from '@/api/article'
import FrontLayout from '@/layouts/FrontLayout.vue'
import CommentSection from '@/components/CommentSection.vue'

interface ArticleVO {
  id: number
  title: string
  summary: string
  content: string
  cover: string
  categoryId: number
  categoryName: string
  viewCount: number
  likeCount: number
  commentCount: number
  publishTime: string
  tags: Array<{ id: number; name: string; color: string }>
}

const route = useRoute()
const router = useRouter()

const article = ref<ArticleVO | null>(null)
const prevArticle = ref<ArticleVO | null>(null)
const nextArticle = ref<ArticleVO | null>(null)
const renderedContent = ref('')

const md = markdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight: (str, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
      } catch (__) {}
    }
    return ''
  }
})

async function fetchArticle() {
  try {
    const id = Number(route.params.id)
    const [articleRes, prevRes, nextRes] = await Promise.all([
      articleApi.getArticleDetail(id),
      articleApi.getPrevArticle(id).catch(() => null),
      articleApi.getNextArticle(id).catch(() => null)
    ])
    article.value = articleRes
    prevArticle.value = prevRes || null
    nextArticle.value = nextRes || null
    renderContent()
  } catch (error) {
    console.error('获取文章详情失败:', error)
    router.push('/404')
  }
}

function renderContent() {
  if (!article.value) return
  const html = md.render(article.value.content)
  const cleanHtml = DOMPurify.sanitize(html)
  renderedContent.value = cleanHtml
}

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

function onCommentCountUpdate(count: number) {
  if (article.value) {
    article.value.commentCount = count
  }
}

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
  background: #fff;
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
  background: #fafafa;
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
  background: #fafafa;
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
  background: #fafafa;
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