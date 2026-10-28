/**
 * 文件上传的接口封装与类型定义。
 */
import request from '@/utils/request'

/** 上传结果 */
export interface UploadResult {
  /** 可直接访问的文件 URL */
  url: string
}

export const fileApi = {
  /**
   * 通用文件上传。
   *
   * <p>后端会按文件头魔数校验真实格式，只允许图片。</p>
   */
  upload(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.upload<UploadResult>('/admin/files/upload', formData)
  },

  /**
   * 编辑器专用上传。
   *
   * <p>返回编辑器期望的 {@code {errno, data}} 结构。</p>
   */
  uploadForEditor(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.upload<{ errno: number; data: string[] }>('/admin/files/upload/editor', formData)
  }
}