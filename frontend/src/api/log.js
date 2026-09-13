import request from '@/utils/request'

// 登录日志分页查询
export function loginLogPage(params) {
  return request.get('/admin/log/login', { params })
}

// 清空登录日志（可传 startTime / endTime 指定时间范围）
export function clearLoginLogs(params) {
  return request.delete('/admin/log/login', { params })
}

// 查询登录日志中某个 IP 的大致位置（手动点击触发，避免频繁调用）
export function queryIpLocation(ip) {
  return request.get('/admin/ip-location/query', { params: { ip } })
}
