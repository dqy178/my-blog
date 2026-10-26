<template>
  <FrontLayout>
    <div class="list-page">
      <div class="page-header">
        <h1 class="page-title">{{ tagName }}</h1>
        <p class="page-description">{{ tagDescription }}</p>
      </div>

      <div class="articles-grid" v-if="articles.length > 0">
        <ArticleCard
          v-for="article in articles"
          :key="article.id"
          :article="article"
        />
      </div>

      <div class="empty-state" v-else>
        <component :is="PriceTag" class="empty-icon" />
        <p>该标签下暂无文章</p>
      </div>

      <Pagination
        v-if="total > pageSize"
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        @page-change="handlePageChange"
      />
    </div>
  </FrontLayout>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { articleApi, ArticleVO } from '@/api/article'
import { tagApi, TagVO } from '@/api/tag'
import ArticleCard from '@/components/ArticleCard.vue'
import Pagination from '@/components/Pagination.vue'
import FrontLayout from '@/layouts/FrontLayout.vue'
import { PriceTag } from '@element-plus/icons-vue'

const route = useRoute()

const tagId = ref(Number(route.params.id))
const tagName = ref('')
const tagDescription = ref('')

const articles = ref<ArticleVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

async function fetchTag() {
  try {
    const res = await tagApi.getTagById(tagId.value)
    if (res) {
      tagName.value = res.name
      tagDescription.value = `包含 ${res.articleCount} 篇文章`
    }
  } catch (error) {
    console.error('获取标签信息失败:', error)
  }
}

async function fetchArticles() {
  try {
    const res = await articleApi.getArticlePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      tagId: tagId.value,
      status: 1
    })
    articles.value = res.list
    total.value = res.total
  } catch (error) {
    console.error('获取文章列表失败:', error)
  }
}

function handlePageChange(page: number) {
  pageNum.value = page
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  fetchTag()
  fetchArticles()
})

watch(() => route.params.id, () => {
  tagId.value = Number(route.params.id)
  pageNum.value = 1
  fetchTag()
  fetchArticles()
})
</script>

<style scoped>
.list-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  padding: 40px 20px;
  margin-bottom: 24px;
  background: linear-gradient(135deg, #6db33f 0%, #8fd14f 100%);
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
  max-width: 600px;
  margin: 0 auto;
}

.articles-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 24px;
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

  .articles-grid {
    grid-template-columns: 1fr;
  }
}
</style>