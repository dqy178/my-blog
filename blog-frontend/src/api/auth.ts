/**
 * 认证相关的接口封装与类型定义。
 *
 * <p>类型与后端 {@code com.blog.vo.UserVO} / {@code com.blog.vo.LoginVO} 一一对应。</p>
 */
import request from '@/utils/request'

/** 登录入参 */
export interface LoginDTO {
  /** 登录用户名 */
  username: string
  /** 登录密码 */
  password: string
  /** 是否记住登录状态（延长 token 有效期） */
  rememberMe?: boolean
}

/** 验证码出参 */
export interface CaptchaVO {
  /** 验证码 ID，提交评论时需原样带回 */
  captchaId: string
  /** 题目，如 3 + 5 = ? */
  question: string
}

/** 密保重置密码入参 */
export interface ForgetPasswordDTO {
  /** 登录用户名 */
  username: string
  /** 安全问题 1 的答案 */
  answer1: string
  /** 安全问题 2 的答案 */
  answer2: string
  /** 新密码 */
  newPassword: string
  /** 确认新密码，仅前端校验用，不提交给后端 */
  confirmPassword: string
}

/** 用户出参 */
export interface UserVO {
  /** 用户 ID */
  id: number
  /** 登录用户名 */
  username: string
  /** 昵称 */
  nickname: string
  /** 邮箱 */
  email: string
  /** 头像 URL */
  avatar: string
  /** 个人简介 */
  bio: string
  /** 座右铭 */
  motto?: string
  /** 角色：ROLE_ADMIN / ROLE_USER */
  role: string
  /** 账号状态：1=正常，0=已禁用 */
  status: number
  /** 最后登录时间 */
  lastLoginTime: string
  /** 创建时间 */
  createTime: string
  /** 文章数（口径：仅统计已发布文章） */
  articleCount?: number
  /** 评论数（口径：全站已审核通过的评论） */
  commentCount?: number
  /** 总浏览量 */
  viewCount?: number
}

/** 登录成功返回 */
export interface LoginVO {
  /** JWT 令牌 */
  token: string
  /** 当前登录用户信息 */
  user: UserVO
}

/**
 * 用原生 fetch 跑登录/注册，绕开 axios 拦截器、Chrome 扩展、ModHeader 等等。
 * 这样能保证登录请求永远不带旧 Authorization 头，永远不会被后端 Jwt 拦截器"先 401"。
 */
async function rawPost<T>(path: string, data: unknown): Promise<T> {
  return rawFetch<T>(path, { method: 'POST', body: JSON.stringify(data) })
}

/**
 * 原生 fetch 请求，行为与 axios 拦截器保持一致（返回 data.data）。
 */
async function rawFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const url = (import.meta.env.VITE_API_BASE_URL || '/api') + path
  const resp = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers || {}) },
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
  /**
   * 登录。
   *
   * <p>刻意使用原生 fetch 而非 axios：避免请求头里带上 localStorage 中的旧 token，
   * 被后端 JWT 拦截器先判 401，导致前端看起来像是「用户名或密码错误」。</p>
   */
  login(data: LoginDTO) {
    return rawPost<LoginVO>('/auth/login', data).then(res => {
      // res 已经是 LoginVO { token, user }，无需再 .data
      return res
    })
  },

  /** 获取算术验证码（游客发表评论时需要） */
  getCaptcha() {
    return rawFetch<CaptchaVO>('/auth/captcha', { method: 'GET' })
  },

  /** 登出，通知后端删除 Redis 中的 token */
  logout() {
    return request.post('/auth/logout')
  },

  /** 获取当前登录用户的资料与统计 */
  getProfile() {
    return request.get<UserVO>('/admin/user/profile')
  },

  /** 更新当前登录用户的资料 */
  updateProfile(data: Partial<UserVO>) {
    return request.put<UserVO>('/admin/user/profile', data)
  },

  /** 修改密码 */
  updatePassword(oldPassword: string, newPassword: string) {
    return request.put('/admin/user/password', { oldPassword, newPassword })
  },

  /** 修改密保答案 */
  updateSecurityAnswers(answer1: string, answer2: string) {
    return request.put('/admin/user/security-answers', { answer1, answer2 })
  },

  /** 通过密保答案重置密码（公开接口） */
  forgetPassword(data: ForgetPasswordDTO) {
    return request.put('/admin/user/forget-password', data)
  }
}