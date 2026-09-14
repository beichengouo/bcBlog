import request from '@/utils/request'

// 后台：表情列表
export function emojiList() {
  return request.get('/admin/emoji/list')
}

// 后台：新增/修改表情
export function saveEmoji(data) {
  return request.post('/admin/emoji/save', data)
}

// 后台：删除表情
export function deleteEmoji(id) {
  return request.delete(`/admin/emoji/${id}`)
}

// 前台：可用表情列表
export function portalEmojiList() {
  return request.get('/portal/emoji/list')
}
