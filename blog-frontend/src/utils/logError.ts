/**
 * 统一的错误日志工具。
 *
 * <p>直接 `logError('xxx:', error)` 会把整个 AxiosError 打出来，
 * 而 AxiosError 的 `config.headers` 里带着 `Authorization: Bearer <token>`，
 * 等于把登录凭据写进了浏览器控制台。这里只打印错误信息本身。</p>
 */
export function logError(message: string, error?: unknown): void {
  if (import.meta.env.DEV) {
    // 开发环境保留详细信息便于调试
    if (error instanceof Error) {
      console.error(message, error.message)
    } else {
      console.error(message, error)
    }
  }
}
