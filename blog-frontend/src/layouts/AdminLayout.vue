<template>
  <el-container class="admin-layout">
    <el-aside :width="isCollapse ? '64px' : '200px'" class="admin-sidebar">
      <div class="sidebar-header" @click="toggleMenu">
        <component :is="Monitor" class="logo-icon" />
        <span class="logo-text" v-if="!isCollapse">个人博客</span>
      </div>
      <transition name="menu-slide">
        <el-menu
          v-show="menuOpen"
          :default-active="activeMenu"
        :collapse="isCollapse"
        :unique-opened="true"
        :collapse-transition="false"
        router
        class="sidebar-menu"
      >
        <template v-for="route in filteredRoutes" :key="route.path">
          <el-menu-item :index="route.path" v-if="!route.children || route.children.length === 0">
            <component :is="getIcon(route.meta?.icon)" class="menu-icon" />
            <template #title>
              <span>{{ route.meta?.title }}</span>
            </template>
          </el-menu-item>
          <el-sub-menu v-else :index="route.path">
            <template #title>
              <component :is="getIcon(route.meta?.icon)" class="menu-icon" />
              <span>{{ route.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in route.children"
              :key="child.path"
              :index="child.path"
            >
              {{ child.meta?.title }}
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
      </transition>
    </el-aside>
    <el-container>
      <el-header class="admin-header">
        <div class="header-left">
          <el-button
            type="text"
            link
            @click="toggleCollapse"
            class="collapse-trigger"
          >
            <component :is="isCollapse ? Expand : Fold" />
          </el-button>
          <h1 class="page-title">{{ pageTitle }}</h1>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click">
            <span class="dropdown-link">
              <img :src="userStore.userInfo?.avatar || '/avatar.png'" class="avatar" alt="头像" />
              <span>{{ userStore.userInfo?.nickname || '管理员' }}</span>
              <component :is="ArrowDown" class="ml-2" />
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goFront">
                  <component :is="HomeFilled" class="mr-2" />
                  前台首页
                </el-dropdown-item>
                <el-dropdown-item divided @click="logout">
                  <component :is="SwitchButton" class="mr-2" />
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="admin-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, watch, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import {
  Monitor,
  Fold,
  Expand,
  ArrowDown,
  User,
  SwitchButton,
  HomeFilled,
  Document,
  Folder,
  PriceTag,
  ChatDotRound,
  Link,
  Menu
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const isCollapse = computed(() => appStore.sidebarOpened === false)

const menuOpen = ref(true)

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

const filteredRoutes = computed(() => {
  const adminRoutes = router.getRoutes().filter(r => r.path === '/admin' && r.children && r.children.length > 0)
  const adminRoute = adminRoutes[adminRoutes.length - 1] || adminRoutes[0]
  const children = adminRoute?.children || []
  return children.filter(r => !r.meta?.hidden)
})

const activeMenu = computed(() => {
  const matched = route.matched.find(m => m.path.startsWith('/admin') && m.path !== '/admin')
  return matched?.path || '/admin/dashboard'
})

const pageTitle = computed(() => {
  const matched = route.matched.find(m => m.meta.title)
  return (matched?.meta?.title as string) || '仪表盘'
})

function getIcon(iconName?: string) {
  if (!iconName) return 'Menu'
  const icons: Record<string, any> = {
    Monitor,
    Document,
    Folder,
    PriceTag,
    ChatDotRound,
    Link,
    User
  }
  return icons[iconName] || 'Menu'
}

function toggleCollapse() {
  appStore.toggleSidebar()
}

function goProfile() {
  router.push('/admin/profile')
}

function goFront() {
  window.open('/', '_blank')
}

function logout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  height: 100vh;
  width: 100%;
  position: relative;
  overflow: hidden;
}

.admin-layout::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: url('/后端背景.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-attachment: fixed;
  opacity: 0.6;
  z-index: -1;
}

.admin-sidebar {
  height: 100%;
  background: #fff;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  height: 80px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  user-select: none;
}

.sidebar-header:hover {
  background: #f5f7fa;
}

.logo {
  display: flex;
  align-items: center;
  color: #303133;
}

.logo-icon {
  font-size: 16px;
  color: #409eff;
  margin-right: 10px;
  width: 20px;
  text-align: center;
}

.logo-text {
  font-size: 25px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
}

.sidebar-menu /deep/ .el-menu-item:first-child {
  margin-top: 30px;
}

  .sidebar-menu /deep/ .el-menu-item,
  .sidebar-menu /deep/ .el-sub-menu__title {
    height: 44px;
    line-height: 44px;
    border-radius: 8px;
    margin: 10px 0;
    padding: 0 16px !important;
    display: flex;
    align-items: center;
    transition: all 0.3s;
    font-size: 20px;
  }

.sidebar-menu /deep/ .el-menu-item:hover,
.sidebar-menu /deep/ .el-sub-menu__title:hover {
  background: #f0f9ff;
}

.sidebar-menu /deep/ .el-menu-item.is-active,
.sidebar-menu /deep/ .el-sub-menu.is-active > .el-sub-menu__title {
  background: #e6f7ff;
  color: #409eff;
}

.sidebar-menu /deep/ .el-menu-item.is-active .menu-icon,
.sidebar-menu /deep/ .el-sub-menu.is-active > .el-sub-menu__title .menu-icon {
  color: #409eff;
}

.menu-slide-enter-active,
.menu-slide-leave-active {
  transition: max-height 0.3s ease, opacity 0.3s ease;
  overflow: hidden;
}

.menu-slide-enter-from,
.menu-slide-leave-to {
  max-height: 0;
  opacity: 0;
}

.menu-slide-enter-to,
.menu-slide-leave-from {
  max-height: 1000px;
  opacity: 1;
}

.menu-icon {
  font-size: 16px;
  margin-right: 10px;
  width: 20px;
  text-align: center;
  color: #909399;
  transition: color 0.3s;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid #ebeef5;
}

.collapse-btn {
  width: 100%;
  justify-content: center;
  color: #909399;
}

.admin-header {
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-trigger {
  color: #606266;
  font-size: 20px;
}

.page-title {
  font-size: 25px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
}

.dropdown-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  color: #303133;
  cursor: pointer;
  transition: all 0.3s;
  white-space: nowrap;
  font-size: 25px;
}

.el-dropdown-menu :deep(.el-dropdown-menu__item) {
  font-size: 20px;
}

.dropdown-link:hover {
  background: #f5f7fa;
}

.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
}

.admin-main {
  background: transparent;
  padding: 24px;
  overflow-y: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .admin-sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 1000;
    transform: translateX(-100%);
    transition: transform 0.3s;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
  }

  .admin-sidebar.open {
    transform: translateX(0);
  }

  .admin-header {
    padding: 0 16px;
  }

  .admin-main {
    padding: 16px;
  }
}
</style>