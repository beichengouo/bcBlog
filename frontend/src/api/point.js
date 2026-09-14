import request from '@/utils/request'

// 后台：发放积分（userIds 为空表示全部普通用户）
export function grantPoints(data) {
  return request.post('/admin/point/grant', data)
}

// 后台：积分流水
export function pointLogs(params) {
  return request.get('/admin/point/logs', { params })
}
