import request from '@/utils/request'

// 后台：照片列表
export function photoList() {
  return request.get('/admin/photo/list')
}

// 后台：新增 / 修改照片
export function savePhoto(data) {
  return request.post('/admin/photo/save', data)
}

// 后台：删除照片
export function deletePhoto(id) {
  return request.delete(`/admin/photo/${id}`)
}

// 前台：照片列表
export function portalPhotoList() {
  return request.get('/portal/photo/list')
}
