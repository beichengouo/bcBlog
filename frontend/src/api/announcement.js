import request from '@/utils/request'

// 后台：公告列表
export function announcementList() {
  return request.get('/admin/announcement/list')
}

// 后台：新增公告
export function addAnnouncement(data) {
  return request.post('/admin/announcement', data)
}

// 后台：修改公告
export function updateAnnouncement(data) {
  return request.put('/admin/announcement', data)
}

// 后台：删除公告
export function deleteAnnouncement(id) {
  return request.delete(`/admin/announcement/${id}`)
}

// 前台：获取当前启用的公告
export function getActiveAnnouncement() {
  return request.get('/portal/announcement/active')
}

// 前台：获取所有启用的公告
export function getActiveAnnouncements() {
  return request.get('/portal/announcement/list')
}
