import request from '@/utils/request'

// 前台：获取 Gitalk 评论配置
export function getGitalkConfig() {
  return request.get('/portal/gitalk/config')
}

// 后台：获取 Gitalk 配置（含 Client Secret）
export function getAdminGitalkConfig() {
  return request.get('/admin/gitalk/config')
}

// 后台：保存 Gitalk 配置
export function saveGitalkConfig(data) {
  return request.post('/admin/gitalk/config', data)
}

// 前台：获取最近评论（默认 10 条）
export function getRecentGitalkComments(limit = 10) {
  return request.get('/portal/gitalk/recent-comments', { params: { limit } })
}
