import request from '@/utils/request'

// 获取一张随机 ACG 封面图片 URL
export function randomAcgCover() {
  return request.get('/admin/acg-cover/random')
}
