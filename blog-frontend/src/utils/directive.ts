import type { App } from 'vue'

export function setupDirective(app: App) {
  // v-focus：自动聚焦
  app.directive('focus', {
    mounted(el: HTMLElement) {
      el.focus()
    }
  })

  // v-copy：点击复制文本
  app.directive('copy', {
    mounted(el: HTMLElement, binding) {
      el.style.cursor = 'pointer'
      el.addEventListener('click', () => {
        navigator.clipboard.writeText(binding.value)
      })
    }
  })
}