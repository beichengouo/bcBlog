import request from '@/utils/request'

// 前台：提交举报（违法有害信息举报入口，需登录）
export function submitReport(data) {
  return request.post('/portal/report', data)
}

// 后台：举报分页（status 为空表示全部，pending 为待处理）
export function adminReportPage(params) {
  return request.get('/admin/report/page', { params })
}

// 后台：处理举报（status: handled 已处理 / ignored 已忽略）
export function handleReport(id, data) {
  return request.put(`/admin/report/${id}/handle`, data)
}
