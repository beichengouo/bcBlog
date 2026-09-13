import request from '@/utils/request'

export function listPortalArticles(params) {
  return request.get('/portal/article/list', { params })
}

export function getPortalArticle(id) {
  return request.get(`/portal/article/${id}`)
}

export function listAdminArticles(params) {
  return request.get('/admin/article/page', { params })
}

// 获取文章编辑数据（含标签ID列表）
export function getArticleForEdit(id) {
  return request.get(`/admin/article/detail/${id}`)
}

// 新增文章
export function saveArticle(data) {
  return request.post('/admin/article/save', data)
}

// 修改文章
export function updateArticle(data) {
  return request.put('/admin/article/update', data)
}

// 删除文章
export function deleteArticle(id) {
  return request.delete(`/admin/article/${id}`)
}
