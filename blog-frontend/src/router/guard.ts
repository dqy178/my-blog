/**
 * 路由守卫。
 *
 * <p>负责三件事：页面标题、登录态校验、角色校验。</p>
 *
 * <p>守卫中需要用到的用户信息可能尚未加载（如刷新页面后 sessionStorage 里有 token
 * 但 userInfo 为空），因此会主动调用 {@code fetchProfile()} 补齐，避免角色判断拿不到 role。</p>
 */
import type { Router } from 'vue-router'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import { useUserStore } from '@/stores/user'

NProgress.configure({ showSpinner: false })

/**
 * 注册路由守卫。
 *
 * @param router 路由实例
 */
export function setupRouterGuard(router: Router) {
  router.beforeEach(async (to, _from, next) => {
    NProgress.start()
    const userStore = useUserStore()

    document.title = `${to.meta.title || '个人博客'} - 个人博客`

    if (to.meta.requiresAuth) {
      if (!userStore.isLoggedIn) {
        userStore.init()
        try {
          await userStore.fetchProfile()
        } catch {
          next({ path: '/login', query: { redirect: to.fullPath } })
          return
        }
      }

      // 有 token 但 userInfo 还没注入（典型场景：Login.vue 登录后立刻 replace），
      // 主动拉一次 profile，避免角色判断拿不到 role 而无限跳 Login
      if (userStore.isLoggedIn && !userStore.userInfo) {
        try {
          await userStore.fetchProfile()
        } catch {
          // 先清本地登录态再跳登录页（logout 内部会通知后端删除 token）
          await userStore.logout()
          next({ path: '/login', query: { redirect: to.fullPath } })
          return
        }
      }

      const roles = to.meta.roles as string[] | undefined
      if (roles && roles.length > 0 && !roles.includes(userStore.userInfo?.role || '')) {
        if (userStore.isLoggedIn) {
          // 角色不匹配：跳回首页。
          // 注意不能跳 Dashboard —— Dashboard 自身也带 roles:['ROLE_ADMIN']，
          // 跳过去会再次触发本分支，形成无限重定向直到 vue-router 抛错。
          ElMessage.warning('没有权限访问该页面')
          next({ path: '/home' })
        } else {
          next({ path: '/login', query: { redirect: to.fullPath } })
        }
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