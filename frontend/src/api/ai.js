import request from '@/utils/request'

// 服务商列表
export function aiProviderList() {
  return request.get('/admin/ai/provider/list')
}

// 新增/修改服务商
export function saveAiProvider(data) {
  return request.post('/admin/ai/provider/save', data)
}

// 删除服务商
export function deleteAiProvider(id) {
  return request.delete(`/admin/ai/provider/${id}`)
}

// 设为默认
export function setDefaultAiProvider(id) {
  return request.put(`/admin/ai/provider/${id}/default`)
}

// 获取服务商的模型列表
export function aiProviderModels(id) {
  return request.get(`/admin/ai/provider/${id}/models`)
}

// AI 生成文章（耗时较长，放宽超时）
export function aiGenerateArticle(data) {
  return request.post('/admin/ai/article', data, { timeout: 120000 })
}
