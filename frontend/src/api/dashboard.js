import request from '@/utils/request'

// 获取仪表盘统计数据
export function getStats() {
  return request.get('/admin/dashboard/stats')
}
