import request from '@/utils/request'

// 后台：歌单列表
export function playlistList() {
  return request.get('/admin/music/playlist')
}

// 后台：新增歌单
export function addPlaylist(data) {
  return request.post('/admin/music/playlist', data)
}

// 后台：删除歌单
export function deletePlaylist(id) {
  return request.delete(`/admin/music/playlist/${id}`)
}

// 后台：切换启用的歌单
export function setActivePlaylist(id) {
  return request.put(`/admin/music/playlist/${id}/active`)
}

// 前台：获取当前启用的歌单 ID
export function getActivePlaylist() {
  return request.get('/portal/music/active')
}
