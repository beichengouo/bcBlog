import request from '@/utils/request'

// 后台：背景列表
export function backgroundList() {
  return request.get('/admin/background/list')
}

// 后台：上传背景（scope：portal / admin）
export function uploadBackground(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/admin/background/upload', fd)
}

// 后台：删除背景
export function deleteBackground(id) {
  return request.delete(`/admin/background/${id}`)
}

// 后台：启用某个背景
export function setActiveBackground(id, scope) {
  return request.put(`/admin/background/${id}/active`, null, { params: { scope } })
}

// 后台：恢复默认背景（不使用任何壁纸，回到主题渐变）
export function clearActiveBackground(scope) {
  return request.put('/admin/background/default', null, { params: { scope } })
}

// 前台：获取当前启用的背景（page 用于前台各页面独立背景：home/photos/resources/sandbox）
export function getActiveBackground(scope, page) {
  return request.get('/portal/background', { params: { scope, page } })
}

// 后台：前台各页面的独立背景设置
export function pageBackgroundList() {
  return request.get('/admin/background/pages')
}

// 后台：保存某个页面的背景设置（mode：follow / none / custom）
export function savePageBackground(pageKey, data) {
  return request.put(`/admin/background/pages/${pageKey}`, data)
}
