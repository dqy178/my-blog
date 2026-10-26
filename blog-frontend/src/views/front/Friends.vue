<template>
  <FrontLayout>
    <div class="friends-page">
      <div class="page-header">
        <h1 class="page-title">友情链接</h1>
        <p class="page-description">志同道合的朋友们</p>
      </div>

      <div class="friends-grid" v-if="friendLinks.length > 0">
        <div
          v-for="link in friendLinks"
          :key="link.id"
          class="friend-card"
        >
          <a :href="link.url" target="_blank" class="friend-link" rel="noopener noreferrer">
            <img :src="link.avatar" :alt="link.name" class="friend-avatar" @error="handleAvatarError($event, link)" />
            <div class="friend-info">
              <h3 class="friend-name">{{ link.name }}</h3>
              <p class="friend-desc">{{ link.description || '暂无描述' }}</p>
            </div>
          </a>
        </div>
      </div>

      <div class="empty-state" v-else>
        <component :is="Link" class="empty-icon" />
        <p>暂无友链</p>
      </div>

      <div class="apply-section">
        <h3 class="apply-title">申请友链</h3>
        <div class="apply-info">
          <p>欢迎交换友链，请在评论区留言或发送邮件，格式如下：</p>
          <pre class="apply-format"><code>名称: 你的博客名称
地址: 你的博客地址
头像: 你的头像链接
描述: 一句话介绍你的博客</code></pre>
          <p class="apply-note">要求：网站内容健康、更新活跃、原创为主</p>
        </div>
      </div>
    </div>
  </FrontLayout>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { friendLinkApi, FriendLinkVO } from '@/api/friendLink'
import FrontLayout from '@/layouts/FrontLayout.vue'
import { Link } from '@element-plus/icons-vue'

const friendLinks = ref<FriendLinkVO[]>([])

async function fetchFriendLinks() {
  try {
    const res = await friendLinkApi.getActiveFriendLinks()
    friendLinks.value = res
  } catch (error) {
    console.error('获取友链失败:', error)
  }
}

function handleAvatarError(event: Event, link: FriendLinkVO) {
  const img = event.target as HTMLImageElement
  img.src = '/friend-avatar.png'
}

onMounted(() => {
  fetchFriendLinks()
})
</script>

<style scoped>
.friends-page {
  width: 100%;
  margin: 0 auto;
  padding: 0 20px;
  box-sizing: border-box;
}

.page-header {
  text-align: center;
  padding: 40px 20px;
  margin-bottom: 32px;
  background: linear-gradient(135deg, #909399 0%, #c0c4cc 100%);
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
}

.friends-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
  margin-bottom: 48px;
}

.friend-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  transition: all 0.3s;
}

.friend-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.friend-link {
  display: flex;
  gap: 16px;
  padding: 20px;
  text-decoration: none;
  color: inherit;
}

.friend-avatar {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  object-fit: cover;
  flex-shrink: 0;
  background: #f5f7fa;
}

.friend-info {
  flex: 1;
  min-width: 0;
}

.friend-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.friend-desc {
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
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

.apply-section {
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.apply-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 2px solid #409eff;
  display: inline-block;
}

.apply-info p {
  color: #606266;
  margin-bottom: 16px;
  line-height: 1.7;
}

.apply-format {
  background: #2d2d2d;
  border-radius: 8px;
  padding: 20px;
  margin: 16px 0;
  overflow-x: auto;
}

.apply-format code {
  color: #f8f8f2;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 14px;
  line-height: 1.6;
}

.apply-note {
  color: #e6a23c !important;
  font-size: 14px !important;
}

@media (max-width: 768px) {
  .page-title {
    font-size: 24px;
  }

  .friends-grid {
    grid-template-columns: 1fr;
  }
}
</style>