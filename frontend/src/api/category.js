import request from '@/utils/request'

// 获取分类树
export function categoryTree() {
  return request.get('/admin/category/tree')
}

// 新增分类
export function saveCategory(data) {
  return request.post('/admin/category/save', data)
}

// 修改分类
export function updateCategory(data) {
  return request.put('/admin/category/update', data)
}

// 删除分类
export function deleteCategory(id) {
  return request.delete(`/admin/category/${id}`)
}
