import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import NProgress from 'nprogress'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
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
        // /auth/login 与 /auth/register 是用于"获取 token" 的接口，
        // 如果带上 localStorage 里的旧 token，会被后端 Jwt 拦截器先 401，
        // 导致前端看起来像是"用户名或密码错误"。这里显式跳过。
        const url = config.url || ''
        const isAuthEndpoint = url.includes('/auth/login') || url.includes('/auth/register')
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
              // 清除本地登录态并跳转登录页
              try {
                localStorage.removeItem('token')
                const userStore = useUserStore()
                userStore.logout?.()
              } catch (e) {}
              if (window.location.pathname !== '/login') {
                window.location.href = '/login'
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

  public request<T>(config: AxiosRequestConfig): Promise<T> {
    return this.instance.request(config)
  }

  public get<T>(url: string, params?: object): Promise<T> {
    return this.instance.get(url, { params })
  }

  public post<T>(url: string, data?: object): Promise<T> {
    return this.instance.post(url, data)
  }

  public put<T>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.put(url, data, config)
  }

  public delete<T>(url: string, params?: object): Promise<T> {
    return this.instance.delete(url, { params })
  }

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
  }
})

export default request