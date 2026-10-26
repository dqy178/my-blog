import request from '@/utils/request'

export interface UploadResult {
  url: string
}

export const fileApi = {
  upload(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.upload<UploadResult>('/admin/files/upload', formData)
  },

  uploadForEditor(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.upload<{ errno: number; data: string[] }>('/admin/files/upload/editor', formData)
  }
}