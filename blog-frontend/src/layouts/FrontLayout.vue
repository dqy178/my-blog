<template>
  <div class="front-layout">
    <header class="front-header">
      <div class="container">
        <router-link to="/home" class="logo">
          <span class="logo-text">个人博客</span>
        </router-link>
        <nav class="nav-menu">
          <router-link to="/home" class="nav-item">首页</router-link>
          <router-link to="/archive" class="nav-item">归档</router-link>
          <router-link to="/about" class="nav-item">关于</router-link>
          <router-link to="/friends" class="nav-item">友链</router-link>
        </nav>
        <div class="header-actions">
          <button class="theme-toggle" :class="{ dark: isDark }" :title="isDark ? '切换到浅色模式' : '切换到暗色模式'" @click="toggleTheme">
            <span class="theme-icon" :class="{ dark: isDark }">
              {{ isDark ? '🌙' : '☀️' }}
            </span>
          </button>
          <router-link v-if="!userStore.isLoggedIn" to="/admin" class="nav-item admin-entry">
            后台管理
          </router-link>
          <div v-if="userStore.isLoggedIn" class="user-menu">
            <el-dropdown trigger="click">
              <span class="dropdown-link">
                <img :src="userStore.userInfo?.avatar || '/avatar.png'" class="avatar" alt="头像" />
                <span>{{ userStore.userInfo?.nickname || '用户' }}</span>
                <component :is="ArrowDown" class="ml-2" />
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="goAdmin">
                    <component :is="Setting" class="mr-2" />
                    后台管理
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="logout">
                    <component :is="SwitchButton" class="mr-2" />
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </header>
    <main class="front-main">
      <div class="container">
        <slot />
      </div>
    </main>
    <footer class="front-footer">
      <div class="container">
        <p>&copy; 2026 个人博客. All rights reserved.</p>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
/**
 * 前台布局：顶部导航 + 内容区 + 页脚。
 *
 * <p>提供暗黑模式切换、登录态入口与登出。</p>
 */
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import {
  ArrowDown,
  SwitchButton,
  Setting
} from '@element-plus/icons-vue'

const userStore = useUserStore()
const appStore = useAppStore()

/** 当前是否为暗黑模式 */
const isDark = computed(() => appStore.isDark)

/** 切换暗黑模式 */
function toggleTheme() {
  appStore.toggleTheme()
}

/** 跳转到后台仪表盘 */
function goAdmin() {
  router.push('/admin/dashboard')
}

/** 登出并返回首页 */
async function logout() {
  // 必须 await：logout 是异步的（先通知后端删 token 再清本地），
  // 不等待的话本地 token 还没清掉，路由守卫仍会认为已登录
  await userStore.logout()
  router.push('/home')
}

import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<style scoped>
.front-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-image: url('/前端背景.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-attachment: fixed;
}

.front-header {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
}

.logo {
  display: flex;
  align-items: center;
  text-decoration: none;
  color: #303133;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.nav-menu {
  display: flex;
  gap: 16px;
  margin-left: 40px;
}

.nav-item {
  padding: 16px 24px;
  color: #303133;
  font-size: 20px;
  font-weight: 600;
  border-radius: 4px;
  transition: all 0.3s;
  text-decoration: none;
  min-width: 70px;
  text-align: center;
  display: inline-block;
}

.nav-item:hover,
.nav-item.router-link-exact-active {
  color: #409eff;
  background: #f0f9ff;
}

.dark .nav-item:hover,
.dark .nav-item.router-link-exact-active {
  background: #1e3a5f;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.theme-toggle {
  width: 40px;
  height: 36px;
  border-radius: 50%;
  transition: all 0.3s;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.theme-icon {
  font-size: 24px;
}

.theme-toggle:not(.dark) .theme-icon {
  color: #000000;
}

.theme-toggle.dark {
  background: #1e3a5f;
}

.theme-toggle.dark .theme-icon {
  color: #cbd5e1;
}

.theme-toggle:hover {
  background: #f5f7fa;
}

.theme-toggle:hover .theme-icon {
  color: #409eff;
}

.theme-toggle.dark:hover {
  background: #1e3a5f;
}

.theme-toggle.dark:hover .theme-icon {
  color: #409eff;
}

.admin-entry {
  padding: 6px 16px;
  background: #409eff;
  color: #fff;
  border-radius: 4px;
  font-size: 14px;
  text-decoration: none;
  transition: all 0.3s;
}

.admin-entry:hover {
  background: #66b1ff;
}

.user-menu {
  position: relative;
}

.dropdown-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 4px;
  color: #303133;
  cursor: pointer;
  transition: all 0.3s;
}

.dropdown-link:hover {
  background: #f5f7fa;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.front-main {
  flex: 1;
  padding: 32px 0;
}

.front-footer {
  background: #fff;
  border-top: 1px solid #ebeef5;
  padding: 24px 0;
  margin-top: auto;
  text-align: center;
  color: #909399;
  font-size: 14px;
}

.front-footer .container {
  justify-content: center;
}

@media (max-width: 768px) {
  .nav-menu {
    display: none;
  }

  .header-content {
    height: 56px;
  }

  .front-main {
    padding: 20px 0;
  }
}
</style>