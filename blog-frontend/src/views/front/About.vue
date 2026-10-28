<template>
  <FrontLayout>
    <div class="about-page">
      <div class="about-header">
        <img :src="avatar" alt="博主头像" class="avatar" />
        <h1 class="name">{{ nickname }}</h1>
        <p class="bio">{{ motto }}</p>
<div class="social-links">
  <a v-for="link in socialLinks" :key="link.label" :href="link.url" target="_blank" class="social-link" :title="link.label" v-html="link.icon"></a>
</div>
      </div>

      <div class="about-content">
        <div class="about-section">
          <h2 class="section-title">关于我</h2>
          <div class="about-text" v-html="renderedIntro"></div>
        </div>

        <div class="about-section">
          <h2 class="section-title">技能栈</h2>
          <div class="skills">
            <el-tag
              v-for="skill in skills"
              :key="skill.id"
              :style="{ backgroundColor: skill.color, color: '#ffffff' }"
              effect="dark"
            >
              {{ skill.name }}
            </el-tag>
          </div>
        </div>

        <div class="about-section">
          <h2 class="section-title">博客信息</h2>
          <div class="stats-grid">
            <div class="stat-card">
              <span class="stat-value">{{ stats.articleCount }}</span>
              <span class="stat-label">文章数</span>
            </div>
            <div class="stat-card">
              <span class="stat-value">{{ stats.categoryCount }}</span>
              <span class="stat-label">分类数</span>
            </div>
            <div class="stat-card">
              <span class="stat-value">{{ stats.tagCount }}</span>
              <span class="stat-label">标签数</span>
            </div>
            <div class="stat-card">
              <span class="stat-value">{{ stats.viewCount }}</span>
              <span class="stat-label">总浏览</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </FrontLayout>
</template>

<script setup lang="ts">
/**
 * 前台「关于我」页。
 *
 * <p>昵称、座右铭、头像与个人简介都从后端个人中心接口实时获取，
 * 与「个人中心」页面展示的数据保持一致（同一份 userInfo）。</p>
 *
 * <p>该路由带 {@code requiresAuth: true}，需登录后才能访问。</p>
 */
import { logError } from '@/utils/logError'
import { onMounted, ref } from 'vue'
import markdownIt from 'markdown-it'
import { safeHtml } from '@/utils/safeHtml'
import { authApi } from '@/api/auth'
import { articleApi } from '@/api/article'
import { categoryApi } from '@/api/category'
import { tagApi, TagVO } from '@/api/tag'
import { StatsVO } from '@/api/stats'
import { useUserStore } from '@/stores/user'
import FrontLayout from '@/layouts/FrontLayout.vue'

const userStore = useUserStore()

/** 博主昵称 */
const nickname = ref('博主')
/** 座右铭 */
const motto = ref('')
/** 头像地址 */
const avatar = ref('/avatar.png')
/** 渲染并净化后的个人简介 HTML */
const renderedIntro = ref('')
/** 技能栈（用标签云呈现） */
const skills = ref<TagVO[]>([])

/** 博客统计数据 */
const stats = ref<StatsVO>({
  articleCount: 0,
  categoryCount: 0,
  tagCount: 0,
  commentCount: 0,
  viewCount: 0,
  visitorCount: 0,
  viewTrend: [],
  articleTrend: []
})

/** 社交链接。icon 为内联 SVG 字符串 */
const socialLinks = ref([
  { label: 'GitHub', url: 'https://github.com', icon: '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z"/></svg>' },
  { label: 'Email', url: 'mailto:admin@example.com', icon: '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z"/></svg>' },
  { label: 'Twitter', url: 'https://twitter.com', icon: '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231 5.45-6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/></svg>' }
])

/**
 * Markdown 渲染器。
 *
 * <p>此处未开启 {@code html: true}，渲染结果仍要经过 {@code safeHtml} 双重保险。</p>
 */
const md = markdownIt()

/** 加载技能栈（复用标签云数据） */
async function fetchTags() {
  try {
    const res = await tagApi.getTagCloud()
    skills.value = res || []
  } catch (error) {
    logError('获取标签云失败:', error)
  }
}

/**
 * 加载关于页数据。
 *
 * <p>三段数据各自独立兜底：</p>
 * <ol>
 *   <li>个人信息：优先实时拉取后端个人中心，失败则回退到 store 中已有的 userInfo</li>
 *   <li>统计数据：文章数/分类数/标签数/总浏览量，任一接口失败都不影响页面渲染</li>
 *   <li>技能栈：单独一个 try，失败时留空</li>
 * </ol>
 */
async function fetchAbout() {
  try {
    let info = userStore.userInfo
    try {
      info = await authApi.getProfile()
      userStore.setUserInfo(info)
    } catch (e) { /* ignore */ }
    if (info) {
      nickname.value = info.nickname || '博主'
      motto.value = info.motto || ''
      avatar.value = info.avatar || '/avatar.png'
      const mdContent = info.bio || '暂无介绍'
      renderedIntro.value = safeHtml(md.render(mdContent))
    }

    try {
      const [articleRes, categoriesRes, tagsRes, archiveRes] = await Promise.all([
        articleApi.getArticlePage({ pageNum: 1, pageSize: 1 }),
        categoryApi.getAllCategories(),
        tagApi.getAllTags(),
        articleApi.getArchive()
      ])
      stats.value.articleCount = articleRes.total
      stats.value.categoryCount = categoriesRes?.length || 0
      stats.value.tagCount = tagsRes?.length || 0
      stats.value.viewCount = (archiveRes || []).reduce((sum: number, a: any) => sum + (a.viewCount || 0), 0)
    } catch (e) { /* ignore */ }
    try {
      await fetchTags()
    } catch (e) { /* ignore */ }
  } catch (error) {
    logError('获取关于信息失败:', error)
  }
}

/** 初始化加载 */
onMounted(() => {
  fetchAbout()
})
</script>

<style scoped>
.about-page {
  width: 100%;
  margin: 0 auto;
  padding: 0 20px;
  box-sizing: border-box;
}

.about-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 60px 20px;
  margin-bottom: 32px;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  border-radius: 16px;
  color: #fff;
}

.avatar {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid rgba(255, 255, 255, 0.3);
  margin-bottom: 20px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.name {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
}

.bio {
  font-size: 16px;
  opacity: 0.9;
  margin-bottom: 24px;
}

.social-links {
  display: flex;
  gap: 16px;
}

.social-link {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  color: #fff;
  font-size: 20px;
  transition: all 0.3s;
  text-decoration: none;
}

.social-link:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: translateY(-2px);
}

.about-content {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.about-section {
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.section-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 2px solid #409eff;
  display: inline-block;
}

.about-text {
  font-size: 16px;
  line-height: 1.8;
  color: #606266;
}

.about-text p {
  margin-bottom: 16px;
}

.skills {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.stat-card {
  text-align: center;
  padding: 24px;
  background: #fafafa;
  border-radius: 10px;
  transition: all 0.3s;
}

.stat-card:hover {
  background: #f0f9ff;
  transform: translateY(-2px);
}

.stat-value {
  display: block;
  font-size: 32px;
  font-weight: 700;
  color: #409eff;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

@media (max-width: 768px) {
  .name {
    font-size: 24px;
  }

  .about-section {
    padding: 24px 20px;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .stat-value {
    font-size: 24px;
  }
}
</style>