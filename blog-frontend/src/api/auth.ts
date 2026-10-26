import request from '@/utils/request'

export interface LoginDTO {
  username: string
  password: string
  rememberMe?: boolean
}

export interface RegisterDTO {
  username: string
  password: string
  confirmPassword: string
  email: string
  captcha: string
}

export interface ForgetPasswordDTO {
  username: string
  answer1: string
  answer2: string
  newPassword: string
}

export interface UserVO {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  bio: string
  motto?: string
  role: string
  status: number
  lastLoginTime: string
  createTime: string
  articleCount?: number
  commentCount?: number
  viewCount?: number
}

export interface LoginVO {
  token: string
  user: UserVO
}

/**
 * 用原生 fetch 跑登录/注册，绕开 axios 拦截器、Chrome 扩展、ModHeader 等等。
 * 这样能保证登录请求永远不带旧 Authorization 头，永远不会被后端 Jwt 拦截器"先 401"。
 */
async function rawPost<T>(path: string, data: unknown): Promise<T> {
  const url = (import.meta.env.VITE_API_BASE_URL || '/api') + path
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
    credentials: 'omit'
  })
  const text = await resp.text()
  let json: any
  try {
    json = text ? JSON.parse(text) : {}
  } catch {
    throw new Error(`后端返回非 JSON: ${text.slice(0, 200)}`)
  }
  if (!resp.ok || (json && json.code !== 200)) {
    throw new Error(json?.message || `HTTP ${resp.status}`)
  }
  // 与 axios 拦截器行为一致：返回 data.data（即真正的业务对象）
  return (json.data ?? json) as T
}

export const authApi = {
  login(data: LoginDTO) {
    return rawPost<LoginVO>('/auth/login', data).then(res => {
      // res 已经是 LoginVO { token, user }，无需再 .data
      return res
    })
  },

  register(data: RegisterDTO) {
    return rawPost<UserVO>('/auth/register', data)
  },

  logout() {
    return request.post('/auth/logout')
  },

  getProfile() {
    return request.get<UserVO>('/admin/user/profile')
  },

  updateProfile(data: Partial<UserVO>) {
    return request.put<UserVO>('/admin/user/profile', data)
  },

  updatePassword(oldPassword: string, newPassword: string) {
    return request.put('/admin/user/password', { oldPassword, newPassword })
  },

  updateSecurityAnswers(answer1: string, answer2: string) {
    return request.put('/admin/user/security-answers', { answer1, answer2 })
  },

  forgetPassword(data: ForgetPasswordDTO) {
    return request.put('/admin/user/forget-password', data)
  }
}