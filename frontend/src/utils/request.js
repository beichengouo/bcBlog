import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 解析请求路径，用于区分后台请求和前台请求
function resolvePath(config) {
  const raw = (config && config.url) || ''
  if (/^https?:\/\//i.test(raw)) {
    try {
      return new URL(raw).pathname
    } catch (e) {
      return raw
    }
  }
  return raw
}

// 前台门户接口：使用普通用户 token（memberToken）
function isPortalRequest(path) {
  return path.startsWith('/portal')
}

// 后台接口和管理员登录接口：使用管理员 token（token）
function isAdminRequest(path) {
  return path.startsWith('/admin') || path.startsWith('/auth')
}

request.interceptors.request.use((config) => {
  const path = resolvePath(config)
  const adminToken = localStorage.getItem('token') || ''
  const memberToken = localStorage.getItem('memberToken') || ''
  let token = ''
  if (isPortalRequest(path)) {
    // 前台只用普通用户 token，后台管理员登录不会让前台自动登录
    token = memberToken
  } else if (isAdminRequest(path)) {
    token = adminToken
  } else {
    token = adminToken || memberToken
  }
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    const path = resolvePath(response.config)
    if (res.code === 401) {
      if (isPortalRequest(path)) {
        // 前台未登录或登录过期，只清理前台 token，不影响后台管理员登录态
        localStorage.removeItem('memberToken')
      } else {
        localStorage.removeItem('token')
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
      }
    } else {
      ElMessage.error(res.msg || '请求失败')
    }
    return Promise.reject(new Error(res.msg || '请求失败'))
  },
  (error) => {
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
