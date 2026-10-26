import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarOpened = ref(true)
  const device = ref<'desktop' | 'mobile'>('desktop')
  const theme = ref<'light' | 'dark'>('light')
  const language = ref<'zh' | 'en'>('zh')
  const loading = ref(false)

  const isMobile = computed(() => device.value === 'mobile')
  const isDark = computed(() => theme.value === 'dark')

  function toggleSidebar() {
    sidebarOpened.value = !sidebarOpened.value
  }

  function closeSidebar() {
    sidebarOpened.value = false
  }

  function openSidebar() {
    sidebarOpened.value = true
  }

  function toggleDevice(newDevice: 'desktop' | 'mobile') {
    device.value = newDevice
    if (newDevice === 'mobile') {
      closeSidebar()
    }
  }

  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    document.documentElement.classList.toggle('dark', theme.value === 'dark')
    localStorage.setItem('theme', theme.value)
  }

  function setTheme(newTheme: 'light' | 'dark') {
    theme.value = newTheme
    document.documentElement.classList.toggle('dark', newTheme === 'dark')
    localStorage.setItem('theme', newTheme)
  }

  function setLanguage(newLanguage: 'zh' | 'en') {
    language.value = newLanguage
    localStorage.setItem('language', newLanguage)
  }

  function setLoading(status: boolean) {
    loading.value = status
  }

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