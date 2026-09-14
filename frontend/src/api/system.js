import request from '@/utils/request'

// 前台：获取系统启动时间、运行时长和服务器时间
export function getSystemInfo() {
  return request.get('/portal/system/info')
}

// 后台：获取性能监控 + 系统信息 + 依赖信息
export function getSystemMonitor() {
  return request.get('/admin/system/monitor')
}

// 后台：立即执行一次数据清理
export function runCleanup() {
  return request.post('/admin/system/cleanup')
}
