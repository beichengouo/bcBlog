import request from '@/utils/request'

// 后台：概览（开关、今日调用与各类型计数、最近活动）
export function siteAiOverview() {
  return request.get('/admin/site-ai/overview')
}

// 后台：读取 / 保存档案与参数
export function siteAiProfile() {
  return request.get('/admin/site-ai/profile')
}

export function saveSiteAiProfile(data) {
  return request.put('/admin/site-ai/profile', data)
}

// 后台：活动日志
export function siteAiActivities(params) {
  return request.get('/admin/site-ai/activities', { params })
}

// 后台：立即执行一次（type: article / comment / status）
export function runSiteAi(type) {
  return request.post('/admin/site-ai/run', null, { params: { type }, timeout: 120000 })
}

// 后台：一键撤销（文章下架 / 评论与回复删除）
export function revertSiteAiActivity(id) {
  return request.post(`/admin/site-ai/activities/${id}/revert`)
}

// 后台：手动补生成今天的记忆
export function summarizeSiteAi() {
  return request.post('/admin/site-ai/memory', null, { timeout: 120000 })
}

// 前台：IRIS 主页
export function irisPortal() {
  return request.get('/portal/iris')
}

// 后台：把已发布文章的封面统一换成当前设置
export function applySiteAiCovers() {
  return request.post('/admin/site-ai/covers/apply')
}

// 后台：预览"今日运行简报"（她会读到哪些数字）
export function siteAiDigest() {
  return request.get('/admin/site-ai/digest')
}

// 后台：重置今日调用计数（测试用，不删日志）
export function resetSiteAiCounters() {
  return request.post('/admin/site-ai/counters/reset')
}
