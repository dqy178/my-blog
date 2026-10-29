/**
 * 站内知识问答的接口封装与类型定义。
 *
 * <p>类型与后端 {@code com.blog.vo.RagAskVO} / {@code com.blog.dto.RagAskDTO} 一一对应。</p>
 *
 * <p>接口本身是公开的（读者无需登录），但后端按 IP 限流；
 * 已登录的管理员在请求头里带上 token 后可豁免限流——
 * {@code request.ts} 的请求拦截器会自动附加，无需本文件处理。</p>
 */
import request from '@/utils/request'

/** 提问入参 */
export interface RagAskDTO {
  /** 用户问题 */
  question: string
  /** 期望召回的块数量，后端会限制在 1~10 */
  topK?: number
}

/** 回答出处 */
export interface RagReferenceVO {
  /** 引用编号，与回答正文中的 [n] 对应 */
  index: number
  /** 文章 ID，前端据此跳转文章详情 */
  articleId: number
  /** 文章标题 */
  articleTitle: string
  /** 标题路径，如「基础 > 鉴权」 */
  headingPath: string
  /** 命中的内容摘要 */
  snippet: string
  /** 检索得分 */
  score: number
}

/** 问答出参 */
export interface RagAskVO {
  /** 模型生成的回答正文，含 [n] 引用标记 */
  answer: string
  /** 出处列表，顺序与回答中的编号一致 */
  references: RagReferenceVO[]
  /** 是否未检索到相关内容 */
  notFound: boolean
  /** 实际召回并使用的块数 */
  retrievedCount: number
  /** 耗时（毫秒），不展示给用户 */
  elapsedMs: number
  /** 使用的模型标识 */
  model: string
}

export const ragApi = {
  /**
   * 提问。
   *
   * <p>后端会先做限流（429）、再检索、最后调大模型（503 表示模型不可用），
   * 这些状态码由 {@code request.ts} 的响应拦截器统一转成错误提示。</p>
   *
   * @param data 问题与 topK
   * @returns 回答与出处列表
   */
  ask(data: RagAskDTO) {
    return request.post<RagAskVO>('/front/rag/ask', data)
  }
}
