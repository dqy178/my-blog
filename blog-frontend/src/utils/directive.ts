import type { App } from 'vue'

/**
 * 注册全局自定义指令。
 *
 * @param app Vue 应用实例
 */
export function setupDirective(app: App) {
  // v-focus：元素挂载后自动聚焦
  app.directive('focus', {
    mounted(el: HTMLElement) {
      el.focus()
    }
  })

  // v-copy：点击元素时把绑定值复制到剪贴板
  app.directive('copy', {
    mounted(el: HTMLElement, binding) {
      el.style.cursor = 'pointer'
      el.addEventListener('click', () => {
        navigator.clipboard.writeText(binding.value)
      })
    }
  })
}