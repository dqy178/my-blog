import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 全局 UI 状态 Store。
 *
 * <p>管理侧边栏开合、响应式断点、主题（暗黑模式）、语言与全局 loading。
 * 主题与语言会写入 localStorage，在 {@link init} 时恢复。</p>
 */
export const useAppStore = defineStore('app', () => {
  /** 侧边栏是否展开 */
  const sidebarOpened = ref(true)
  /** 当前设备类型。移动端会自动收起侧边栏 */
  const device = ref<'desktop' | 'mobile'>('desktop')
  /** 主题：light / dark */
  const theme = ref<'light' | 'dark'>('light')
  /** 界面语言 */
  const language = ref<'zh' | 'en'>('zh')
  /** 全局 loading 标记 */
  const loading = ref(false)

  /** 是否为移动端 */
  const isMobile = computed(() => device.value === 'mobile')
  /** 是否为暗黑模式 */
  const isDark = computed(() => theme.value === 'dark')

  /** 切换侧边栏开合状态 */
  function toggleSidebar() {
    sidebarOpened.value = !sidebarOpened.value
  }

  /** 收起侧边栏 */
  function closeSidebar() {
    sidebarOpened.value = false
  }

  /** 展开侧边栏 */
  function openSidebar() {
    sidebarOpened.value = true
  }

  /**
   * 切换设备类型。切到移动端时自动收起侧边栏。
   */
  function toggleDevice(newDevice: 'desktop' | 'mobile') {
    device.value = newDevice
    if (newDevice === 'mobile') {
      closeSidebar()
    }
  }

  /**
   * 切换暗黑模式。
   *
   * <p>同步给根元素的 {@code dark} class，由 CSS 变量驱动实际配色。</p>
   */
  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    document.documentElement.classList.toggle('dark', theme.value === 'dark')
    localStorage.setItem('theme', theme.value)
  }

  /**
   * 设置指定主题。
   */
  function setTheme(newTheme: 'light' | 'dark') {
    theme.value = newTheme
    document.documentElement.classList.toggle('dark', newTheme === 'dark')
    localStorage.setItem('theme', newTheme)
  }

  /**
   * 设置界面语言。
   */
  function setLanguage(newLanguage: 'zh' | 'en') {
    language.value = newLanguage
    localStorage.setItem('language', newLanguage)
  }

  /**
   * 设置全局 loading 状态。
   */
  function setLoading(status: boolean) {
    loading.value = status
  }

  /**
   * 从 localStorage 恢复主题与语言（应用启动时调用）。
   */
  function init() {
    const storedTheme = localStorage.getItem('theme') as 'light' | 'dark' | null
    if (storedTheme === 'dark') {
      theme.value = 'dark'
      document.documentElement.classList.add('dark')
    } else {
      theme.value = 'light'
      document.documentElement.classList.remove('dark')
    }

    const storedLanguage = localStorage.getItem('language') as 'zh' | 'en' | null
    if (storedLanguage) {
      language.value = storedLanguage
    }
  }

  return {
    sidebarOpened,
    device,
    theme,
    language,
    loading,
    isMobile,
    isDark,
    toggleSidebar,
    closeSidebar,
    openSidebar,
    toggleDevice,
    toggleTheme,
    setTheme,
    setLanguage,
    setLoading,
    init
  }
})