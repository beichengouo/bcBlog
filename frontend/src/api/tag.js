import request from '@/utils/request'

// 获取全部标签
export function tagList() {
  return request.get('/admin/tag/list')
}

// 前台获取全部标签（游客可访问）
export function portalTagList() {
  return request.get('/portal/tag/list')
}

// 新增标签
export function saveTag(data) {
  return request.post('/admin/tag/save', data)
}

// 修改标签
export function updateTag(data) {
  return request.put('/admin/tag/update', data)
}

// 删除标签
export function deleteTag(id) {
  return request.delete(`/admin/tag/${id}`)
}
