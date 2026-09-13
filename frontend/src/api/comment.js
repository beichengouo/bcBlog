import request from '@/utils/request'

// 前台：获取文章评论
export function listComments(params) {
  return request.get('/portal/comment/list', { params })
}

// 前台：发表评论
export function saveComment(data) {
  return request.post('/portal/comment/save', data)
}

// 后台：分页查询评论
export function adminCommentPage(params) {
  return request.get('/admin/comment/page', { params })
}

// 后台：修改评论状态
export function updateCommentStatus(data) {
  return request.put('/admin/comment/status', data)
}

// 后台：删除评论
export function deleteComment(id) {
  return request.delete(`/admin/comment/${id}`)
}
