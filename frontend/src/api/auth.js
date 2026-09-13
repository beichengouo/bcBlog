import request from '@/utils/request'

export function getCaptcha() {
  return request.get('/auth/captcha')
}

export function login(data) {
  return request.post('/auth/login', data)
}

export function logout() {
  return request.post('/admin/auth/logout')
}

export function getInfo() {
  return request.get('/admin/auth/info')
}

// 修改当前管理员密码
export function changePassword(data) {
  return request.post('/admin/auth/change-password', data)
}
