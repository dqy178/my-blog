/**
 * Axios 请求封装。
 *
 * <p>统一处理三件事：</p>
 * <ol>
 *   <li>请求拦截：附加 {@code Authorization: Bearer <token>}</li>
 *   <li>响应拦截：拆掉 {@code {code, message, data}} 外壳，成功时直接返回 {@code data}</li>
 *   <li>错误处理：按 HTTP 状态码统一弹提示（401 跳登录、403/404/409/500 等）</li>
 * </ol>
 *
 * <p>因此调用方拿到的泛型 {@code T} 就是业务对象，而非 AxiosResponse。</p>
 */
import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'
import NProgress from 'nprogress'

/** API 基础路径。开发环境由 Vite 代理转发到后端 */
const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
/** 请求超时时间（毫秒） */
const TIMEOUT = 10000

class Request {
  private instance: AxiosInstance

  constructor(config: AxiosRequestConfig) {
    this.instance = axios.create(config)
    this.setupInterceptors()
  }

  private setupInterceptors() {
    this.instance.interceptors.request.use(
      (config: InternalAxiosRequestConfig) => {
        NProgress.start()
        // /auth/login 是用于"获取 token" 的接口，
        // 如果带上 localStorage 里的旧 token，会被后端 Jwt 拦截器先 401，
        // 导致前端看起来像是"用户名或密码错误"。这里显式跳过。
        const url = config.url || ''
        const isAuthEndpoint = url.includes('/auth/login')
        const userStore = useUserStore()
        if (userStore.token && !isAuthEndpoint) {
          config.headers.Authorization = `Bearer ${userStore.token}`
        } else if (isAuthEndpoint && config.headers) {
          // 显式删除，避免 axios 复用 keep-alive 连接时带上旧 header
          delete (config.headers as any).Authorization
        }
        return config
      },
      (error) => {
        NProgress.done()
        return Promise.reject(error)
      }
    )

    this.instance.interceptors.response.use(
      (response: AxiosResponse) => {
        NProgress.done()
        const { data } = response
        if (data && data.code === 200) {
          return data.data
        }
        ElMessage.error(data?.message || '请求失败')
        return Promise.reject(new Error(data?.message || '请求失败'))
      },
      (error) => {
        NProgress.done()
        const { response } = error
        if (response) {
          // 后端业务异常会带 {code, message, data:null}；优先取 message
          const serverMsg = (response.data && (response.data.message || response.data.msg)) as string | undefined
          switch (response.status) {
            case 401:
              // 清除本地登录态并跳转登录页。
              // 这里只清本地，不通知后端：token 已失效，再调 logout 会再发一次
              // 请求并再次 401，形成递归。
              try {
                localStorage.removeItem('token')
                localStorage.removeItem('userInfo')
                sessionStorage.removeItem('token')
                sessionStorage.removeItem('userInfo')
              } catch (e) { /* ignore */ }
              if (window.location.pathname !== '/login') {
                // 用 SPA 跳转而不是 window.location.href：
                // 后者会整页刷新并丢掉 query，导致「登录后跳回原页面」失效
                const redirect = window.location.pathname + window.location.search
                router.replace({ path: '/login', query: { redirect } }).catch(() => {
                  window.location.href = '/login'
                })
              }
              ElMessage.error(serverMsg || '登录已过期，请重新登录')
              break
            case 403:
              ElMessage.error(serverMsg || '没有权限访问')
              break
            case 404:
              ElMessage.error(serverMsg || '请求资源不存在')
              break
            case 409:
              ElMessage.error(serverMsg || '资源冲突，操作被拒绝')
              break
            case 500:
              ElMessage.error(serverMsg || '服务器内部错误')
              break
            default:
              ElMessage.error(serverMsg || '请求失败')
          }
        } else if (axios.isAxiosError(error) && error.code === 'ECONNABORTED') {
          ElMessage.error('请求超时，请稍后重试')
        } else {
          ElMessage.error('网络错误，请检查网络连接')
        }
        return Promise.reject(error)
      }
    )
  }

  // 注意：响应拦截器中已把结果拆包为 data.data（业务负载），
  // 因此这里的泛型 T 指的是业务对象，而非 AxiosResponse。
  public request<T>(config: AxiosRequestConfig): Promise<T> {
    return this.instance.request(config) as unknown as Promise<T>
  }

  /**
   * GET 请求。
   *
   * @param url    接口路径
   * @param params 查询参数
   */
  public get<T>(url: string, params?: object): Promise<T> {
    return this.instance.get(url, { params }) as unknown as Promise<T>
  }

  /**
   * POST 请求。
   *
   * @param url  接口路径
   * @param data 请求体
   */
  public post<T>(url: string, data?: object | null): Promise<T> {
    return this.instance.post(url, data) as unknown as Promise<T>
  }

  /**
   * PUT 请求。
   *
   * @param url    接口路径
   * @param data   请求体
   * @param config 额外配置（如 query 参数）
   */
  public put<T>(url: string, data?: object | null, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.put(url, data, config) as unknown as Promise<T>
  }

  /**
   * DELETE 请求。
   *
   * @param url    接口路径
   * @param params 查询参数
   */
  public delete<T>(url: string, params?: object): Promise<T> {
    return this.instance.delete(url, { params }) as unknown as Promise<T>
  }

  /**
   * 批量删除。
   *
   * <p>后端批量接口形如 DELETE /xxx/batch?data=1,2,3，
   * 由 @RequestParam("data") List<Long> 接收（Spring 原生支持逗号分隔）。</p>
   *
   * @param url 接口路径
   * @param ids ID 列表
   */
  public deleteBatch<T>(url: string, ids: number[]): Promise<T> {
    return this.instance.delete(url, { params: { data: ids } }) as unknown as Promise<T>
  }

  /**
   * 文件上传。
   *
   * @param url      接口路径
   * @param formData 表单数据
   */
  public upload<T>(url: string, formData: FormData): Promise<T> {
    // instance.post 同样会经过 response 拦截器，在拦截器中我们已 return data.data
    return this.instance.post(url, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }) as unknown as Promise<T>
  }
}

export const request = new Request({
  baseURL: BASE_URL,
  timeout: TIMEOUT,
  headers: {
    'Content-Type': 'application/json'
  },
  // 数组参数序列化为逗号分隔：data=1,2,3
  //
  // axios 默认会把数组序列化成 data[]=1&data[]=2，虽然 Tomcat 会剥离参数名里的 []，
  // 让 @RequestParam("data") List<Long> 恰好能接住，但那是容器的实现细节而非 HTTP 规范，
  // 换 Undertow/Jetty 即失效。逗号分隔是 Spring 的原生能力，跨容器稳定。
  paramsSerializer: {
    serialize(params: Record<string, any>) {
      const parts: string[] = []
      Object.entries(params ?? {}).forEach(([key, value]) => {
        if (value === null || value === undefined) return
        if (value instanceof Date) {
          // 与 axios 默认行为保持一致: ISO 8601
          parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(value.toISOString())}`)
        } else if (Array.isArray(value)) {
          // 空数组不产生任何参数，由后端按缺失参数返回 400
          if (value.length === 0) return
          parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(value.join(','))}`)
        } else {
          parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
        }
      })
      return parts.join('&')
    }
  }
})
export default request
