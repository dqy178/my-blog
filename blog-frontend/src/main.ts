import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'virtual:uno.css'
import './styles/index.scss'
import 'nprogress/nprogress.css'

import { setupDirective } from './utils/directive'
import { setupRouterGuard } from './router/guard'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
const _userStore = useUserStore()
const _appStore = useAppStore()
_userStore.init()
_appStore.init()
app.use(router)
app.use(ElementPlus)

setupDirective(app)
setupRouterGuard(router)

// 全局兜底：避免单个组件错误导致整个应用白屏
app.config.errorHandler = (err, _instance, info) => {
  console.error('[Vue error]', info, err)
  // Element Plus 全局消息给出可读反馈
  try {
    import('element-plus').then(({ ElMessage }) => {
      ElMessage.error((err as Error)?.message || '组件加载失败')
    })
  } catch (_) {
    /* noop */
  }
}
window.addEventListener('unhandledrejection', (e) => {
  console.error('[Unhandled promise rejection]', e.reason)
})

app.mount('#app')