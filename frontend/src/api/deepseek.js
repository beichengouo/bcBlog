import request from '@/utils/request'

// 获取已保存的 DeepSeek API Key
export function getApiKey() {
  return request.get('/admin/deepseek/api-key')
}

// 保存 DeepSeek API Key
export function saveApiKey(apiKey) {
  return request.post('/admin/deepseek/api-key', { apiKey })
}

// 查询 DeepSeek 账户余额
export function getBalance() {
  return request.get('/admin/deepseek/balance')
}

// AI 一键写文章（生成耗时较长，单独放宽超时）
export function generateArticle(requirement) {
  return request.post('/admin/deepseek/article', { requirement }, { timeout: 120000 })
}
