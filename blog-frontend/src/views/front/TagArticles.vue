<template>
  <FrontLayout>
    <div class="list-page">
      <div class="page-header">
        <h1 class="page-title">{{ tagName }}</h1>
        <p class="page-description">{{ tagDescription }}</p>
      </div>

      <div v-if="articles.length > 0" class="articles-grid">
        <ArticleCard
            v-for="article in articles"
            :key="article.id"
            :article="article"
            :scope="{ tagId: tagId }"
          />
      </div>

      <div v-else class="empty-state">
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
/**
 * 前台标签文章列表页。
 *
 * <p>从标签云进入的文章列表，布局与分类页等其它前台页面保持一致。</p>
 */
import { logError } from '@/utils/logError'
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { articleApi, ArticleVO } from '@/api/article'
import { tagApi } from '@/api/tag'
import ArticleCard from '@/components/ArticleCard.vue'
import Pagination from '@/components/Pagination.vue'
import FrontLayout from '@/layouts/FrontLayout.vue'
import { PriceTag } from '@element-plus/icons-vue'

const route = useRoute()

/** 当前标签 ID */
const tagId = ref(Number(route.params.id))
/** 标签名称 */
const tagName = ref('')
/** 标签描述（此处展示该标签下的文章数） */
const tagDescription = ref('')

/** 当前页文章 */
const articles = ref<ArticleVO[]>([])
/** 文章总数 */
const total = ref(0)
/** 当前页码 */
const pageNum = ref(1)
/** 每页条数 */
const pageSize = ref(10)

/** 加载标签信息 */
async function fetchTag() {
  try {
    const res = await tagApi.getTagById(tagId.value)
    if (res) {
      tagName.value = res.name
      tagDescription.value = `包含 ${res.articleCount} 篇文章`
    }
  } catch (error) {
    logError('获取标签信息失败:', error)
  }
}

/** 加载该标签下的文章（固定只取已发布） */
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
    logError('获取文章列表失败:', error)
  }
}

/**
 * 分页变化：更新页码/页大小后重新拉取数据并回到顶部。
 *
 * @param page 新页码
 * @param size 新的每页条数（可选）
 */
function handlePageChange(page: number, size?: number) {
    pageNum.value = page
    if (size && size !== pageSize.value) {
      pageSize.value = size
    }
    // 必须重新拉取数据：只改 pageNum 不会触发请求，列表会一直停在第一页。
    fetchArticles()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

/** 初始化加载 */
onMounted(() => {
  fetchTag()
  fetchArticles()
})

/**
 * 监听路由参数变化。
 *
 * <p>切换标签时回到第 1 页并重新加载标签信息与文章列表。</p>
 */
watch(() => route.params.id, () => {
  tagId.value = Number(route.params.id)
  pageNum.value = 1
  fetchTag()
  fetchArticles()
})
</script>

<style scoped>
.list-page {
  /* 与归档页、关于页、友链页、搜索页保持一致：全宽 + 20px 内边距，
     不再额外限制 1200px，避免在宽屏上与其它前台页面大小不一致。 */
  width: 100%;
  margin: 0 auto;
  padding: 0 20px;
  box-sizing: border-box;
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