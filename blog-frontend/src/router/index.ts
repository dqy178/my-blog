import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import NProgress from 'nprogress'

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
    meta: { title: '关于我', layout: 'front' }
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
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  }
})

export function setupRouterGuard(router: ReturnType<typeof createRouter>) {
  router.beforeEach(async (to, from, next) => {
    NProgress.start()
    const userStore = useUserStore()
    const appStore = useAppStore()

    document.title = `${to.meta.title || '个人博客'} - 个人博客`

    if (to.meta.requiresAuth) {
      if (!userStore.isLoggedIn) {
        userStore.init()
      }

      if (userStore.isLoggedIn) {
        try {
          await userStore.fetchProfile()
        } catch (e) {
          userStore.logout()
          next({ name: 'Login', query: { redirect: to.fullPath } })
          return
        }
      }

      if (!userStore.isLoggedIn) {
        next({ name: 'Login', query: { redirect: to.fullPath } })
        return
      }

      const roles = to.meta.roles as string[] | undefined
      if (roles && roles.length > 0 && !roles.includes(userStore.userInfo?.role || '')) {
        next({ name: 'Dashboard' })
        return
      }
    }

    if (to.name === 'Login' && userStore.isLoggedIn) {
      next({ name: 'Dashboard' })
      return
    }

    next()
  })

  router.afterEach(() => {
    NProgress.done()
  })
}

export default router