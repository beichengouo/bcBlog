import request from '@/utils/request'

// 管理员列表
export function adminUserList() {
  return request.get('/admin/user/list')
}

// 注册下级管理员
export function registerAdmin(data) {
  return request.post('/admin/user/register', data)
}

// 修改下级管理员（角色/菜单/昵称/状态/密码）
export function updateAdmin(data) {
  return request.put('/admin/user/update', data)
}

// 删除下级管理员
export function deleteAdmin(id) {
  return request.delete(`/admin/user/${id}`)
}
