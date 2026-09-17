import request from '@/utils/request'

// 安全设置状态：是否已设置安全密码 / 本次是否已验证 / 安全邮箱 / 各种登录保护开关
export function securityStatus() {
  return request.get('/admin/security/status')
}

// 二次验证：输入安全密码（必须设置过安全密码才可能通过）
export function securityVerify(password) {
  return request.post('/admin/security/verify', { password })
}

// 设置 / 修改安全密码（需要登录密码确认身份）
export function saveSecurityPassword(data) {
  return request.post('/admin/security/password', data)
}

// 保存安全设置：安全邮箱、二次验证开关、异常登录提醒、验证有效期、单点登录等
export function saveSecuritySettings(data) {
  return request.post('/admin/security/settings', data)
}

// 在线会话列表
export function securitySessions() {
  return request.get('/admin/security/sessions')
}

// 踢下线（只注销该会话，不封号）
export function kickSession(token) {
  return request.post('/admin/security/sessions/kick', null, { params: { token } })
}
