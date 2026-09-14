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

// 后台：查询百度 IP 定位 AK
export function getIpLocationAk() {
  return request.get('/admin/config/ip-location-ak')
}

// 后台：保存百度 IP 定位 AK
export function saveIpLocationAk(apiKey) {
  return request.post('/admin/config/ip-location-ak', { apiKey })
}

// 后台：查询 IP 定位服务商（baidu / gaode）
export function getIpLocationProvider() {
  return request.get('/admin/config/ip-location-provider')
}

// 后台：切换 IP 定位服务商
export function saveIpLocationProvider(provider) {
  return request.put('/admin/config/ip-location-provider', null, { params: { provider } })
}

// 后台：查询高德 IP 定位 Key
export function getGaodeIpKey() {
  return request.get('/admin/config/gaode-ip-key')
}

// 后台：保存高德 IP 定位 Key
export function saveGaodeIpKey(apiKey) {
  return request.post('/admin/config/gaode-ip-key', { apiKey })
}

// 后台：查询后台背景透明度
export function getAdminBgOpacity() {
  return request.get('/admin/config/admin-bg-opacity')
}

// 后台：保存后台背景透明度
export function saveAdminBgOpacity(opacity) {
  return request.put('/admin/config/admin-bg-opacity', null, { params: { opacity } })
}

// 后台：查询 ACG 随机封面 Token
export function getAcgCoverToken() {
  return request.get('/admin/config/acg-cover-token')
}

// 后台：保存 ACG 随机封面 Token
export function saveAcgCoverToken(apiKey) {
  return request.post('/admin/config/acg-cover-token', { apiKey })
}
