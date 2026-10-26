import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserVO, LoginVO } from '@/api/auth'
import { authApi } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const userInfo = ref<UserVO | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ROLE_ADMIN')

  function setToken(newToken: string) {
    token.value = newToken
    sessionStorage.setItem('token', newToken)
    localStorage.removeItem('token')
  }

  function setUserInfo(info: UserVO) {
    userInfo.value = info
    sessionStorage.setItem('userInfo', JSON.stringify(info))
    localStorage.removeItem('userInfo')
  }

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

  async function login(loginData: { username: string; password: string; rememberMe?: boolean }) {
    const res = await authApi.login(loginData)
    // 拦截器已经统一拆掉 Result.data 包装，这里 res 本身就是 LoginVO
    setToken(res.token)
    setUserInfo(res.user)
    return res
  }

  async function register(registerData: { username: string; password: string; confirmPassword: string; email: string; captcha: string }) {
    const res = await authApi.register(registerData)
    return res
  }

  async function fetchProfile() {
    const res = await authApi.getProfile()
    // 拦截器已经统一拆掉包装，res 本身就是 UserVO
    setUserInfo(res)
    return res
  }

  async function updateProfile(data: Partial<UserVO>) {
    const res = await authApi.updateProfile(data)
    setUserInfo(res)
    return res
  }

  async function updatePassword(oldPassword: string, newPassword: string) {
    await authApi.updatePassword(oldPassword, newPassword)
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('userInfo')
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

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
    register,
    fetchProfile,
    updateProfile,
    updatePassword,
    logout,
    init
  }
})