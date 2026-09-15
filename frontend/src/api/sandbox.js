import request from '@/utils/request'

// ---------------- 后台：沙盒世界管理 ----------------

// 世界（地图背景 + 世界观设定）
export function sandboxWorld() {
  return request.get('/admin/sandbox/world')
}

export function saveSandboxWorld(data) {
  return request.post('/admin/sandbox/world', data)
}

// 地图地点
export function sandboxLocations() {
  return request.get('/admin/sandbox/locations')
}

export function saveSandboxLocation(data) {
  return request.post('/admin/sandbox/locations', data)
}

export function deleteSandboxLocation(id) {
  return request.delete(`/admin/sandbox/locations/${id}`)
}

// 运行参数（开关、间隔、夜间静默、每日上限、低语积分）
export function sandboxSettings() {
  return request.get('/admin/sandbox/settings')
}

export function saveSandboxSettings(data) {
  return request.post('/admin/sandbox/settings', data)
}

// 角色
export function sandboxCharacters() {
  return request.get('/admin/sandbox/characters')
}

export function saveSandboxCharacter(data) {
  return request.post('/admin/sandbox/characters', data)
}

export function deleteSandboxCharacter(id) {
  return request.delete(`/admin/sandbox/characters/${id}`)
}

// 立即执行一次（AI 调用可能比较慢，单独放宽超时时间）
export function runSandboxCharacter(id) {
  return request.post(`/admin/sandbox/characters/${id}/run`, null, { timeout: 180000 })
}

// 一键让全部启用角色行动一轮（多角色依次行动，便于互相遇见，耗时较长）
export function runAllSandboxCharacters() {
  return request.post('/admin/sandbox/run-all', null, { timeout: 600000 })
}

// 行动日志
export function sandboxActs(params) {
  return request.get('/admin/sandbox/acts', { params })
}

export function deleteSandboxAct(id) {
  return request.delete(`/admin/sandbox/acts/${id}`)
}

// 旅人低语
export function sandboxInteractions(params) {
  return request.get('/admin/sandbox/interactions', { params })
}

export function deleteSandboxInteraction(id) {
  return request.delete(`/admin/sandbox/interactions/${id}`)
}

// ---------------- 前台：沙盒世界 ----------------

export function portalSandbox() {
  return request.get('/portal/sandbox')
}

export function portalSandboxActs(params) {
  return request.get('/portal/sandbox/acts', { params })
}

export function portalSandboxInteractions(params) {
  return request.get('/portal/sandbox/interactions', { params })
}

export function portalSandboxWhisper(data) {
  return request.post('/portal/sandbox/whisper', data)
}

// 用积分为角色贡献金币
export function portalSandboxCoin(data) {
  return request.post('/portal/sandbox/coin', data)
}

// 某个角色的金币流水
export function portalSandboxCoins(params) {
  return request.get('/portal/sandbox/coins', { params })
}

// 后台：金币流水
export function sandboxCoinLogs(params) {
  return request.get('/admin/sandbox/coins', { params })
}

export function deleteSandboxCoinLog(id) {
  return request.delete(`/admin/sandbox/coins/${id}`)
}

// 后台：角色之间的好感度
export function sandboxRelations(params) {
  return request.get('/admin/sandbox/relations', { params })
}

export function saveSandboxRelation(data) {
  return request.post('/admin/sandbox/relations', data)
}

export function deleteSandboxRelation(id) {
  return request.delete(`/admin/sandbox/relations/${id}`)
}
