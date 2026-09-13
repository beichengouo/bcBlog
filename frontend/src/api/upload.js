import request from '@/utils/request'

// 上传图片，返回可访问的 URL 路径
export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/admin/upload/image', formData)
}
