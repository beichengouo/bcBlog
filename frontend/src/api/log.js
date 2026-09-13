import request from '@/utils/request'

// 登录日志分页查询
export function loginLogPage(params) {
  return request.get('/admin/log/login', { params })
}

// 清空登录日志（可传 startTime / endTime 指定时间范围）
export function clearLoginLogs(params) {
  return request.delete('/admin/log/login', { params })
}
