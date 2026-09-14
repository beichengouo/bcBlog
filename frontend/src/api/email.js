import request from '@/utils/request'

// 邮件配置
export function getEmailConfig() {
  return request.get('/admin/email/config')
}

export function saveEmailConfig(data) {
  return request.post('/admin/email/config', data)
}

// 邮件模板
export function emailTemplates() {
  return request.get('/admin/email/templates')
}

export function saveEmailTemplate(data) {
  return request.post('/admin/email/template', data)
}

export function deleteEmailTemplate(id) {
  return request.delete(`/admin/email/template/${id}`)
}

export function activateEmailTemplate(id) {
  return request.post(`/admin/email/template/${id}/active`)
}

// 测试发送
export function testEmail(data) {
  return request.post('/admin/email/test', data)
}
