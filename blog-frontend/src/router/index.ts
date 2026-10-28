/**
 * 路由表与路由实例。
 *
 * <p>所有页面组件均使用懒加载，按需下载对应 chunk。</p>
 *
 * <p>meta 字段约定：</p>
 * <ul>
 *   <li>{@code title}：页面标题，会拼接到 document.title</li>
 *   <li>{@code layout}：布局类型（front / admin / empty），由 App.vue 决定外层容器</li>
 *   <li>{@code requiresAuth}：是否需要登录，守卫会拦截未登录访问</li>
 *   <li>{@code roles}：允许访问的角色列表</li>
 *   <li>{@code icon}：后台侧边栏图标（Element Plus 图标名）</li>
 *   <li>{@code hidden}：是否在后台菜单中隐藏</li>
 *   <li>{@code activeMenu}：高亮的菜单路径（用于「编辑」这类详情页）</li>
 * </ul>
 *
 * <p>鉴权逻辑统一实现在 {@link ./guard.ts}，本文件只负责路由表与实例。</p>
 */
import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/home'
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/front/Home.vue'),
    meta: { title: '首页', layout: 'front' }
  },
  {
    path: '/article/:id',
    name: 'ArticleDetail',
    component: () => import('@/views/front/ArticleDetail.vue'),
    meta: { title: '文章详情', layout: 'front' }
  },
  {
    path: '/category/:id',
    name: 'CategoryArticles',
    component: () => import('@/views/front/CategoryArticles.vue'),
    meta: { title: '分类文章', layout: 'front' }
  },
  {
    path: '/tag/:id',
    name: 'TagArticles',
    component: () => import('@/views/front/TagArticles.vue'),
    meta: { title: '标签文章', layout: 'front' }
  },
  {
    path: '/archive',
    name: 'Archive',
    component: () => import('@/views/front/Archive.vue'),
    meta: { title: '归档', layout: 'front' }
  },
  {
    path: '/about',
    name: 'About',
    component: () => import('@/views/front/About.vue'),
    meta: { title: '关于我', layout: 'front', requiresAuth: true }
  },
  {
    path: '/friends',
    name: 'Friends',
    component: () => import('@/views/front/Friends.vue'),
    meta: { title: '友链', layout: 'front' }
  },
  {
    path: '/search',
    name: 'Search',
    component: () => import('@/views/front/Search.vue'),
    meta: { title: '搜索', layout: 'front' }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/admin/Login.vue'),
    meta: { title: '登录', layout: 'empty' }
  },
  {
    path: '/forget-password',
    name: 'ForgetPassword',
    component: () => import('@/views/admin/ForgetPassword.vue'),
    meta: { title: '找回密码', layout: 'empty' }
  },
  {
    path: '/admin',
    redirect: '/admin/dashboard'
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, roles: ['ROLE_ADMIN'] },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'Monitor' }
      },
      {
        path: 'articles',
        name: 'ArticleManage',
        component: () => import('@/views/admin/article/ArticleList.vue'),
        meta: { title: '文章管理', icon: 'Document' }
      },
      {
        path: 'articles/create',
        name: 'ArticleCreate',
        component: () => import('@/views/admin/article/ArticleEdit.vue'),
        meta: { title: '新建文章', hidden: true, activeMenu: '/admin/articles' }
      },
      {
        path: 'articles/edit/:id',
        name: 'ArticleEdit',
        component: () => import('@/views/admin/article/ArticleEdit.vue'),
        meta: { title: '编辑文章', hidden: true, activeMenu: '/admin/articles' }
      },
      {
        path: 'categories',
        name: 'CategoryManage',
        component: () => import('@/views/admin/category/CategoryList.vue'),
        meta: { title: '分类管理', icon: 'Folder' }
      },
      {
        path: 'tags',
        name: 'TagManage',
        component: () => import('@/views/admin/tag/TagList.vue'),
        meta: { title: '标签管理', icon: 'PriceTag' }
      },
      {
        path: 'comments',
        name: 'CommentManage',
        component: () => import('@/views/admin/comment/CommentList.vue'),
        meta: { title: '评论管理', icon: 'ChatDotRound' }
      },
      {
        path: 'friend-links',
        name: 'FriendLinkManage',
        component: () => import('@/views/admin/friendLink/FriendLinkList.vue'),
        meta: { title: '友链管理', icon: 'Link' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/admin/Profile.vue'),
        meta: { title: '个人中心', icon: 'User' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/404.vue'),
    meta: { title: '404', layout: 'empty' }
  }
]

const router = createRouter({
  // 使用 HTML5 History 模式，生产环境需由 Nginx 的 try_files 兜底到 index.html
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    // 浏览器前进/后退时恢复原滚动位置，其他情况回到页面顶部
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  }
})

// 路由守卫统一在 ./guard 中实现（setupRouterGuard），本文件只负责路由表与实例
export default router