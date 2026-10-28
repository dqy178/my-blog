<template>
  <article class="article-card" :class="{ 'has-cover': article.cover }" style="cursor: pointer;" @click="goToArticle">
    <router-link v-if="article.cover" :to="articleLink" class="cover-link" @click.stop>
      <img :src="article.cover" :alt="article.title" class="cover-image" />
    </router-link>
    <div class="card-content">
      <div class="card-meta">
        <router-link :to="`/category/${article.categoryId}`" class="category-tag">
          {{ article.categoryName }}
        </router-link>
        <span class="publish-time">{{ formatDate(article.publishTime || article.createTime) }}</span>
      </div>
      <router-link :to="articleLink" class="title-link" @click.stop>
        <h2 class="card-title">{{ article.title }}</h2>
      </router-link>
      <p v-if="article.summary" class="card-summary">{{ article.summary }}</p>
      <div class="card-footer">
        <div class="tags">
          <span
            v-for="tag in (article.tagNames || []).slice(0, 3)"
            :key="tag"
            class="tag"
          >
            #{{ tag }}
          </span>
        </div>
<div class="stats">
          <span class="stat">           
            {{ article.viewCount }} 阅读
          </span>                       
          <span class="stat">
            {{ (realCommentCount ?? article.commentCount) }} 评论
          </span>
        </div>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
/**
 * 文章卡片。
 *
 * <p>展示封面、分类、发布时间、标题、摘要、标签与统计信息。
 * 整卡可点击进入文章详情，内部链接使用 @click.stop 阻止冒泡避免重复跳转。</p>
 *
 * <p>通过 {@link Props.scope} 传递「作用域上下文」：从标签页或分类页进入时带上，
 * 文章详情页的上下篇就会在该集合内切换，而不是跳到全站不相干标签的内容。</p>
 */
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { commentApi } from '@/api/comment'
import type { ArticleVO } from '@/api/article'

const routerInst = useRouter()

interface Props {
  /** 要展示的文章 */
  article: ArticleVO
  /**
   * 上下篇作用域上下文。
   * 从标签页传入 {@code tagId}，从分类页传入 {@code categoryId}；
   * 不传则文章详情页按全站范围切换上下篇。
   */
  scope?: {
    /** 限定标签 */
    tagId?: number
    /** 限定分类 */
    categoryId?: number
  }
}

const props = defineProps<Props>()
const article = computed(() => props.article)

/**
 * 作用域对应的查询参数。
 *
 * <p>只有存在作用域时才写入 query，避免 URL 里出现 {@code ?tag=undefined} 这类无效参数。
 * 参数走 URL 而非路由 state，是为了让刷新页面、复制链接分享都能保持正确的上下文。</p>
 */
const scopeQuery = computed<Record<string, number>>(() => {
  const query: Record<string, number> = {}
  if (props.scope?.tagId) query.tag = props.scope.tagId
  if (props.scope?.categoryId) query.category = props.scope.categoryId
  return query
})

/** 文章详情页的跳转目标（携带作用域上下文） */
const articleLink = computed(() => ({
  path: `/article/${article.value.id}`,
  query: scopeQuery.value
}))

/**
 * 实际评论数（含回复）。
 *
 * <p>后端返回的 article.commentCount 只统计已通过审核的评论，
 * 而卡片上希望显示的是文章详情页看到的完整数量（含未通过的回复）。
 * 因此这里异步拉取评论树并递归统计；请求失败时回退到后端返回的 commentCount。</p>
 */
const realCommentCount = ref<number | null>(null)

// 递归统计评论数（含回复）
function countComments(list: any[]): number {
  let count = 0
  for (const c of list) {
    count++
    if (c.children && c.children.length) count += countComments(c.children)
  }
  return count
}

onMounted(async () => {
  try {
    const comments = await commentApi.getCommentsByArticleId(article.value.id)
    realCommentCount.value = countComments(comments)
  } catch {
    // ignore
  }
})

/**
 * 跳转到文章详情页。
 *
 * <p>整卡点击与内部链接走同一个目标，保证作用域上下文不会丢失。</p>
 */
function goToArticle() {
  routerInst.push(articleLink.value)
}

/**
 * 格式化时间为「yyyy-MM-dd HH:mm:ss」。
 *
 * @param dateStr 时间字符串
 * @returns 格式化后的时间；入参为空时返回空串
 */
function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
</script>

<style scoped>
.article-card {
  background: rgba(255, 255, 255, 0.5);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.article-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.cover-link {
  display: block;
  height: 180px;
  overflow: hidden;
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s;
}

.article-card:hover .cover-image {
  transform: scale(1.05);
}

.card-content {
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.category-tag {
  padding: 4px 10px;
  background: #f0f9ff;
  color: #409eff;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.3s;
}

.category-tag:hover {
  background: #d9ecff;
}

.publish-time {
  font-size: 14px;
  color: #000;
}

.title-link {
  text-decoration: none;
  color: inherit;
}

.card-title {
  font-size: 18px;
  font-weight: 400;
  color: #303133;
  line-height: 1.4;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.3s;
}

.title-link:hover .card-title {
  color: #409eff;
}

.card-summary {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 16px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.tag {
  font-size: 12px;
  color: #909399;
  transition: color 0.3s;
}

.tag:hover {
  color: #409eff;
}

.stats {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 16px;
  color: #303133;
}

.stat-icon {
  font-size: 20px;
  color: #303133;
}

.article-card:not(.has-cover) .card-content {
  padding-top: 20px;
}
</style>