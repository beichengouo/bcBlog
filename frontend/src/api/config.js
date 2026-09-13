import request from '@/utils/request'

// 后台：获取站点设置
export function getConfig() {
  return request.get('/admin/config')
}

// 后台：保存站点设置
export function saveConfig(data) {
  return request.put('/admin/config', data)
}

// 前台：获取站点设置
export function getPortalConfig() {
  return request.get('/portal/config')
}
