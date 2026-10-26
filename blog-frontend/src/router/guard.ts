import type { Router } from 'vue-router'
import NProgress from 'nprogress'
import { useUserStore } from '@/stores/user'

NProgress.configure({ showSpinner: false })

export function setupRouterGuard(router: Router) {
  router.beforeEach(async (to, _from, next) => {
    NProgress.start()
    const userStore = useUserStore()

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
          userStore.logout()
          next({ path: '/login', query: { redirect: to.fullPath } })
          return
        }
      }

      const roles = to.meta.roles as string[] | undefined
      if (roles && roles.length > 0 && !roles.includes(userStore.userInfo?.role || '')) {
        if (userStore.isLoggedIn) {
          next({ name: 'Dashboard' })
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