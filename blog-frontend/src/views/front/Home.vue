<template>
  <FrontLayout>
    <div class="home-page">
      <section class="hero-section">
        <div class="hero-content">
          <h1 class="hero-title">欢迎来到我的个人博客</h1>
          <p class="hero-subtitle">记录技术成长，分享生活感悟</p>
          <router-link to="/archive" class="hero-btn">开始阅读</router-link>
        </div>
      </section>

      <section class="latest-articles">
        <div class="section-header">
          <h2 class="section-title">最新文章</h2>
          <router-link to="/archive" class="view-more">查看更多</router-link>
        </div>
        <div v-if="articles.length > 0" class="articles-grid">
          <ArticleCard
            v-for="article in articles"
            :key="article.id"
            :article="article"
          />
        </div>
        <div v-else class="empty-state">
          <p>暂无文章</p>
        </div>
      </section>

      <section class="sidebar-section">
        <div class="sidebar-grid">
          <div class="sidebar-card">
            <h3 class="sidebar-title">分类</h3>
            <div class="category-list">
              <router-link
                v-for="category in categories"
                :key="category.id"
                :to="`/category/${category.id}`"
                class="category-item"
              >
                <span class="category-name">{{ category.name }}</span>
                <span class="category-count">{{ category.articleCount }}</span>
              </router-link>
            </div>
          </div>

          <div class="sidebar-card">
            <h3 class="sidebar-title">热门标签</h3>
            <div class="tag-cloud">
              <router-link
                v-for="tag in tags"
                :key="tag.id"
                :to="`/tag/${tag.id}`"
                class="tag-item"
                :style="{ backgroundColor: tag.color + '20', color: tag.color }"
              >
                {{ tag.name }}
              </router-link>
            </div>
          </div>

          <div class="sidebar-card">
            <h3 class="sidebar-title">热门文章</h3>
            <div class="hot-articles">
              <router-link
                v-for="article in hotArticles"
                :key="article.id"
                :to="`/article/${article.id}`"
                class="hot-article-item"
              >
                <el-tooltip :content="article.title" placement="top" :show-after="300">
  <span class="hot-article-title">{{ article.title }}</span>
</el-tooltip>
                <span class="hot-article-views">{{ article.viewCount }} 阅读</span>
              </router-link>
            </div>
          </div>
        </div>
      </section>
    </div>
  </FrontLayout>
</template>

<script setup lang="ts">
/**
 * 前台首页。
 *
 * <p>聚合展示最新文章、热门文章、分类导航与标签云。四份数据并发请求，
 * 任一失败只记录日志，不影响其余板块渲染。</p>
 */
import { logError } from '@/utils/logError'
import { onMounted, ref } from 'vue'
import { articleApi, ArticleVO } from '@/api/article'
import { categoryApi, CategoryVO } from '@/api/category'
import { tagApi, TagVO } from '@/api/tag'
import ArticleCard from '@/components/ArticleCard.vue'
import FrontLayout from '@/layouts/FrontLayout.vue'

/** 最新文章列表 */
const articles = ref<ArticleVO[]>([])
/** 分类列表 */
const categories = ref<CategoryVO[]>([])
/** 标签云 */
const tags = ref<TagVO[]>([])
/** 热门文章列表 */
const hotArticles = ref<ArticleVO[]>([])

/** 并发加载首页所需的四份数据 */
async function fetchData() {
  try {
    const [articlesRes, categoriesRes, tagsRes, hotRes] = await Promise.all([
      articleApi.getLatestArticles(6),
      categoryApi.getAllCategories(),
      tagApi.getTagCloud(),
      articleApi.getHotArticles(6)
    ])
    articles.value = articlesRes
    categories.value = categoriesRes
    tags.value = tagsRes
    hotArticles.value = hotRes
  } catch (error) {
    logError('获取首页数据失败:', error)
  }
}

/** 初始化加载 */
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.home-page {
  max-width: 1200px;
  margin: 0 auto;
}

.hero-section {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  border-radius: 16px;
  padding: 80px 40px;
  text-align: center;
  color: #fff;
  margin-bottom: 40px;
}

.hero-title {
  font-size: 48px;
  font-weight: 700;
  margin-bottom: 16px;
  line-height: 1.2;
}

.hero-subtitle {
  font-size: 20px;
  opacity: 0.9;
  margin-bottom: 32px;
}

.hero-btn {
  display: inline-block;
  padding: 14px 40px;
  background: #fff;
  color: #409eff;
  border-radius: 50px;
  font-size: 16px;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.3s;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.hero-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.2);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.section-title {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.view-more {
  color: #000;
  font-size: 18px;
  font-weight: 700;
  text-decoration: none;
  transition: color 0.3s;
}

.view-more:hover {
  color: #000;
}

.articles-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 24px;
}

.sidebar-section {
  margin-top: 40px;
}

.sidebar-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.sidebar-card {
  background: rgba(255, 255, 255, 0.5);
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.sidebar-title {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.category-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.category-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-radius: 8px;
  text-decoration: none;
  transition: all 0.3s;
  font-size: 20px;
  color: #000;
}

.category-item:hover {
  background: #f5f7fa;
  color: #409eff;
}

.category-count {
  background: #f0f9ff;
  color: #000;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 20px;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-item {
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 20px;
  color: #000;
  text-decoration: none;
  transition: all 0.3s;
}

.tag-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.hot-articles {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hot-article-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-radius: 6px;
  text-decoration: none;
  color: #606266;
  transition: all 0.3s;
}

.hot-article-item:hover {
  background: #f5f7fa;
  color: #409eff;
}

.hot-article-title {
  font-size: 20px;
  color: #000;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hot-article-views {
  font-size: 20px;
  color: #000;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #909399;
}

@media (max-width: 992px) {
  .sidebar-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .hero-section {
    padding: 50px 24px;
  }

  .hero-title {
    font-size: 32px;
  }

  .hero-subtitle {
    font-size: 16px;
  }

  .articles-grid {
    grid-template-columns: 1fr;
  }

  .sidebar-grid {
    grid-template-columns: 1fr;
  }
}
</style>