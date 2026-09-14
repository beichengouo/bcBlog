import request from '@/utils/request'

// 后台：资源列表
export function resourceList() {
  return request.get('/admin/resource/list')
}

// 后台：新增 / 修改资源
export function saveResource(data) {
  return request.post('/admin/resource/save', data)
}

// 后台：删除资源
export function deleteResource(id) {
  return request.delete(`/admin/resource/${id}`)
}

// 前台：资源列表
export function portalResourceList() {
  return request.get('/portal/resource/list')
}

// 前台：资源详情
export function portalResourceDetail(id) {
  return request.get(`/portal/resource/${id}`)
}

// 前台：消耗积分解锁资源
export function unlockResource(id) {
  return request.post(`/portal/resource/${id}/unlock`)
}
