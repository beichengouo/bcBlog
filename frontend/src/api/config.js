import request from '@/utils/request'

// 后台：获取站点设置
export function getConfig() {
  return request.get('/admin/config')
}

// 后台：保存站点设置
export function saveConfig(data) {
  return request.put('/admin/config', data)
}

// 后台：上传并设置站点 Logo
export function uploadSiteLogo(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/admin/config/logo', fd)
}

// 后台：删除当前 Logo，恢复为默认头像
export function deleteSiteLogo() {
  return request.delete('/admin/config/logo')
}

// 前台：获取站点设置
export function getPortalConfig() {
  return request.get('/portal/config')
}

// 后台：设置看板娘是否在前台显示
export function setLive2dEnabled(enabled) {
  return request.put('/admin/config/live2d-enabled', null, { params: { enabled } })
}
