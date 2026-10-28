import type { InjectionKey } from 'vue'

/**
 * 评论验证码上下文。
 *
 * <p>评论有两处提交入口：文章下方的「开楼」主表单，以及某条评论下方展开的
 * 内联回复表单。为了让两处都受后端的验证码 / 频率限制 / 内容净化约束，
 * 验证码状态由评论区统一持有并通过 provide/inject 下发。</p>
 */
export interface CommentCaptchaState {
  /** 验证码 ID */
  id: string
  /** 题目，如 3 + 5 = ? */
  question: string
  /** 用户填写的答案（两处表单共用同一个值） */
  answer: string
}

export interface CommentCaptchaContext {
  /** 当前是否需要验证码（管理员不需要） */
  readonly required: boolean
  /** 验证码状态 */
  readonly state: CommentCaptchaState
  /** 重新获取一道验证码 */
  refresh: () => Promise<void>
}

export const COMMENT_CAPTCHA_KEY: InjectionKey<CommentCaptchaContext> = Symbol('commentCaptcha')
