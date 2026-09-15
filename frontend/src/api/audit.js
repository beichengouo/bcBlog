import request from '@/utils/request'

// 后台：API 调用审计列表（仅超级管理员）
export function auditList(params) {
  return request.get('/admin/audit/list', { params })
}

// 后台：按管理员汇总调用次数
export function auditSummary() {
  return request.get('/admin/audit/summary')
}
