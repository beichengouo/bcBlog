import request from '@/utils/request'

// 前台：获取当前展示的看板娘模型
export function getActiveLive2dModel() {
  return request.get('/portal/live2d/active')
}

// 前台：获取可选模型列表（游客可访问）
export function getPortalLive2dModels() {
  return request.get('/portal/live2d/list')
}

// 后台：模型列表
export function live2dModelList() {
  return request.get('/admin/live2d/list')
}

// 后台：新增自定义模型
export function addLive2dModel(data) {
  return request.post('/admin/live2d', data)
}

// 后台：删除模型
export function deleteLive2dModel(id) {
  return request.delete(`/admin/live2d/${id}`)
}

// 后台：切换当前展示的模型
export function setActiveLive2dModel(id) {
  return request.put(`/admin/live2d/${id}/active`)
}
