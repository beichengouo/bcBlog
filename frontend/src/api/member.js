import request from '@/utils/request'

/* ===== 前台用户 ===== */

// 发送邮箱验证码（当前阶段接口会直接返回验证码用于测试）
export function sendEmailCode(email) {
  return request.post('/portal/user/email-code', { email })
}

// 注册
export function registerMember(data) {
  return request.post('/portal/user/register', data)
}

// 登录
export function loginMember(data) {
  return request.post('/portal/user/login', data)
}

// 退出登录
export function logoutMember() {
  return request.post('/portal/user/logout')
}

// 当前用户信息
export function getMemberInfo() {
  return request.get('/portal/user/info')
}

// 签到
export function memberSignIn() {
  return request.post('/portal/user/sign-in')
}

// 上传头像
export function uploadMemberAvatar(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/portal/user/avatar', fd)
}

// 我的评论
export function myComments(params) {
  return request.get('/portal/user/my-comments', { params })
}

// 我的邀请码
export function myInviteCode() {
  return request.get('/portal/user/invite-code')
}

// 我的积分流水
export function myPointLogs(params) {
  return request.get('/portal/user/points', { params })
}

/* ===== 后台管理 ===== */

// 普通用户列表
export function memberPage() {
  return request.get('/admin/member/list')
}

// 修改普通用户
export function updateMember(data) {
  return request.put('/admin/member/update', data)
}

// 删除普通用户
export function deleteMember(id) {
  return request.delete(`/admin/member/${id}`)
}

// 等级列表
export function levelList() {
  return request.get('/admin/level/list')
}

// 保存等级
export function saveLevel(data) {
  return request.post('/admin/level/save', data)
}

// 删除等级
export function deleteLevel(id) {
  return request.delete(`/admin/level/${id}`)
}

// 邀请码列表
export function inviteList() {
  return request.get('/admin/invite/list')
}

// 设置邀请码权限
export function updateInvitePermission(userId, canInvite) {
  return request.put(`/admin/invite/${userId}/permission`, null, { params: { canInvite } })
}

// 重新生成邀请码
export function regenerateInvite(userId) {
  return request.put(`/admin/invite/${userId}/regenerate`)
}

// 生成/获取当前管理员的邀请码
export function generateMyInvite() {
  return request.post('/admin/invite/mine')
}
