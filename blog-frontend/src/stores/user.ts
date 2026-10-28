import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserVO } from '@/api/auth'
import { authApi } from '@/api/auth'
import { logError } from '@/utils/logError'

/**
 * 登录态 Store。
 *
 * <p>令牌与用户信息保存在 sessionStorage（关闭标签页即失效），
 * 写入时同时清理 localStorage 中的历史残留，避免共享电脑自动登录。</p>
 */
export const useUserStore = defineStore('user', () => {
  /** JWT 令牌 */
  const token = ref<string>('')
  /** 当前登录用户信息 */
  const userInfo = ref<UserVO | null>(null)

  /** 是否已登录（以是否存在令牌为准） */
  const isLoggedIn = computed(() => !!token.value)
  /** 是否为管理员。路由守卫与验证码逻辑都依赖它 */
  const isAdmin = computed(() => userInfo.value?.role === 'ROLE_ADMIN')

  /**
   * 保存令牌。
   *
   * <p>写 sessionStorage 并清理 localStorage，避免两种存储混用造成状态不一致。</p>
   */
  function setToken(newToken: string) {
    token.value = newToken
    sessionStorage.setItem('token', newToken)
    localStorage.removeItem('token')
  }

  /**
   * 保存用户信息。
   */
  function setUserInfo(info: UserVO) {
    userInfo.value = info
    sessionStorage.setItem('userInfo', JSON.stringify(info))
    localStorage.removeItem('userInfo')
  }

  /**
   * 从 sessionStorage 恢复登录态。
   *
   * <p>仅使用 sessionStorage，不再读取 localStorage 中的旧 token。
   * userInfo 解析失败时直接清除，避免脏数据影响角色判断。</p>
   */
  function loadFromStorage() {
    // 仅使用 sessionStorage，不再读取 localStorage 中的旧 token，避免共享电脑自动登录
    const storedToken = sessionStorage.getItem('token')
    const storedUserInfo = sessionStorage.getItem('userInfo')
    if (storedToken) {
      token.value = storedToken
    }
    if (storedUserInfo) {
      try {
        userInfo.value = JSON.parse(storedUserInfo)
      } catch {
        sessionStorage.removeItem('userInfo')
      }
    }
    // 清理可能残留的旧 localStorage 登录数据
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  /**
   * 登录：调用接口并保存令牌与用户信息。
   */
  async function login(loginData: { username: string; password: string; rememberMe?: boolean }) {
    const res = await authApi.login(loginData)
    // 拦截器已经统一拆掉 Result.data 包装，这里 res 本身就是 LoginVO
    setToken(res.token)
    setUserInfo(res.user)
    return res
  }

  /**
   * 拉取最新的用户信息（用于刷新页面后恢复用户信息、修改资料后同步）。
   */
  async function fetchProfile() {
    const res = await authApi.getProfile()
    // 拦截器已经统一拆掉包装，res 本身就是 UserVO
    setUserInfo(res)
    return res
  }

  /**
   * 更新个人资料并同步本地状态。
   */
  async function updateProfile(data: Partial<UserVO>) {
    const res = await authApi.updateProfile(data)
    setUserInfo(res)
    return res
  }

  /**
   * 修改密码。
   */
  async function updatePassword(oldPassword: string, newPassword: string) {
    await authApi.updatePassword(oldPassword, newPassword)
  }

  /**
   * 登出。
   * @param notifyServer 是否通知后端删除 token。
   *        传 false 用于「token 已失效」的场景（如收到 401）：
   *        此时再调后端 logout 会再发一次请求并再次 401，形成递归。
   */
  async function logout(notifyServer = true) {
    if (notifyServer) {
      try {
        await authApi.logout()
      } catch (e) {
        // 网络异常也要继续清本地状态，否则用户会卡在「看似已登录」的状态
        logError('登出请求失败', e)
      }
    }
    token.value = ''
    userInfo.value = null
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('userInfo')
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  /**
   * 初始化登录态（应用启动时调用）。
   */
  function init() {
    loadFromStorage()
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isAdmin,
    setToken,
    setUserInfo,
    loadFromStorage,
    login,
    fetchProfile,
    updateProfile,
    updatePassword,
    logout,
    init
  }
})