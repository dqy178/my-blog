<template>
  <article class="article-card" :class="{ 'has-cover': article.cover }" @click="goToArticle" style="cursor: pointer;">
    <router-link :to="`/article/${article.id}`" class="cover-link" v-if="article.cover" @click.stop>
      <img :src="article.cover" :alt="article.title" class="cover-image" />
    </router-link>
    <div class="card-content">
      <div class="card-meta">
        <router-link :to="`/category/${article.categoryId}`" class="category-tag">
          {{ article.categoryName }}
        </router-link>
        <span class="publish-time">{{ formatDate(article.publishTime || article.createTime) }}</span>
      </div>
      <router-link :to="`/article/${article.id}`" class="title-link" @click.stop>
        <h2 class="card-title">{{ article.title }}</h2>
      </router-link>
      <p class="card-summary" v-if="article.summary">{{ article.summary }}</p>
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
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { commentApi } from '@/api/comment'

const routerInst = useRouter()

interface ArticleVO {
  id: number
  title: string
  summary: string
  cover: string
  categoryId: number
  categoryName: string
  viewCount: number
  likeCount: number
  commentCount: number
  publishTime: string
  createTime: string
  tagNames: string[]
}

interface Props {
  article: ArticleVO
}

const props = defineProps<Props>()
const article = computed(() => props.article)
const realCommentCount = ref<number | null>(null)

onMounted(async () => {
  try {
    const comments = await commentApi.getCommentsByArticleId(article.value.id)
    let count = 0
    function countAll(list: any[]) {
      for (const c of list) {
        count++
        if (c.children && c.children.length) countAll(c.children)
      }
    }
    countAll(comments)
    realCommentCount.value = count
  } catch {
    // ignore
  }
})

function goToArticle() {
  routerInst.push(`/article/${article.value.id}`)
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}
</script>

<style scoped>
.article-card {
  background: #fff;
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