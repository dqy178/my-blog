<template>
  <el-container class="admin-layout">
    <el-aside :width="isCollapse ? '64px' : '200px'" class="admin-sidebar">
      <div class="sidebar-header" @click="toggleMenu">
        <component :is="Monitor" class="logo-icon" />
        <span v-if="!isCollapse" class="logo-text">个人博客</span>
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
          <el-menu-item v-if="!route.children || route.children.length === 0" :index="adminPath(route.path)">
            <component :is="getIcon(route.meta?.icon as string | undefined)" class="menu-icon" />
            <template #title>
              <span>{{ route.meta?.title }}</span>
            </template>
          </el-menu-item>
          <el-sub-menu v-else :index="adminPath(route.path)">
            <template #title>
<component :is="getIcon(route.meta?.icon as string | undefined)" class="menu-icon" />
              <span>{{ route.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in route.children"
              :key="child.path"
              :index="adminPath(child.path)"
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
            link
            class="collapse-trigger"
            @click="toggleCollapse"
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
/**
 * 后台布局：左侧菜单 + 顶部工具栏 + 内容区。
 *
 * <p>菜单项从路由表动态生成（过滤掉 hidden 的详情页），
 * 因此新增后台页面只需改路由，无需再改菜单。</p>
 */
import { computed, ref } from 'vue'
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
    Link
  } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

/** 侧边栏是否折叠 */
const isCollapse = computed(() => appStore.sidebarOpened === false)

/** 用户下拉菜单展开状态 */
const menuOpen = ref(true)

/** 展开/收起用户下拉菜单 */
function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

/**
 * 从路由表生成侧边栏菜单项。
 *
 * <p>取 {@code /admin} 父路由的 children，过滤掉 {@code meta.hidden} 的详情页
 * （如「新建文章」「编辑文章」），这些页面通过父级菜单进入，不单独显示。</p>
 */
const filteredRoutes = computed(() => {
  const adminRoutes = router.getRoutes().filter(r => r.path === '/admin' && r.children && r.children.length > 0)
  const adminRoute = adminRoutes[adminRoutes.length - 1] || adminRoutes[0]
  const children = adminRoute?.children || []
  return children.filter(r => !r.meta?.hidden)
})

/**
 * 当前高亮的菜单项。
 *
 * <p>优先使用路由 meta 指定的激活菜单，例如「新建文章」「编辑文章」高亮「文章管理」。</p>
 */
const activeMenu = computed(() => {
  // 优先使用路由 meta 指定的激活菜单，例如「新建文章」「编辑文章」高亮「文章管理」
  const metaActive = route.meta.activeMenu as string | undefined
  if (metaActive) {
    return metaActive
  }
  const matched = route.matched.find(m => m.path.startsWith('/admin') && m.path !== '/admin')
  return matched?.path || '/admin/dashboard'
})

/**
 * 把 admin 子路由记录的 path 转成绝对路径。
 *
 * 注意：router.getRoutes() 返回的子路由记录里，path 是相对路径（如 'tags'、'articles'），
 * 不是 '/admin/tags'。el-menu 的 router 模式会把 index 直接交给 router.push，
 * 相对路径会按当前地址解析：在 /admin/articles/create 下 push('tags') 会变成
 * /admin/articles/tags，该路径不存在，于是落到 404 路由。
 */
function adminPath(path: string) {
  return path.startsWith('/') ? path : `/admin/${path}`
}

/** 当前页面标题（取自路由 meta） */
const pageTitle = computed(() => {
  const matched = route.matched.find(m => m.meta.title)
  return (matched?.meta?.title as string) || '仪表盘'
})

/**
 * 根据路由 meta 中的图标名取 Element Plus 图标组件。
 *
 * @param iconName 图标名
 * @returns 图标组件；未匹配时返回通用的 'Menu'
 */
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

/** 折叠/展开侧边栏 */
function toggleCollapse() {
  appStore.toggleSidebar()
}

/** 在新标签页打开前台站点 */
function goFront() {
  window.open('/', '_blank')
}

/** 登出并跳回登录页 */
async function logout() {
  // 必须 await：logout 是异步的（先通知后端删 token 再清本地），
  // 不等待的话本地 token 还没清掉，路由守卫仍会认为已登录
  await userStore.logout()
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
  background: rgba(255, 255, 255, 0.3);
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

.sidebar-menu :deep(.el-menu-item:first-child) {
  margin-top: 30px;
}

.sidebar-menu :deep(.el-menu) {
  background: rgba(255, 255, 255, 0.3) !important;
}

  .sidebar-menu :deep(.el-menu-item),
  .sidebar-menu :deep(.el-sub-menu__title) {
    height: 44px;
    line-height: 44px;
    border-radius: 8px;
    margin: 10px 0;
    padding: 0 16px !important;
    display: flex;
    align-items: center;
    transition: all 0.3s;
    font-size: 20px;
    background: rgba(255, 255, 255, 0.3) !important;
  }

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(240, 249, 255, 0.6) !important;
}

.sidebar-menu :deep(.el-menu-item.is-active),
.sidebar-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  background: rgba(230, 247, 255, 0.8) !important;
  color: #409eff;
}

.sidebar-menu :deep(.el-menu-item.is-active .menu-icon),
.sidebar-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title .menu-icon) {
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
  background: rgba(255, 255, 255, 0.3);
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