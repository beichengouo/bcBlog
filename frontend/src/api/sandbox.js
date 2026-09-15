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

// AI 一键创作角色卡（结合当前世界观，耗时长一些）
export function generateSandboxCharacter(data) {
  return request.post('/admin/sandbox/characters/generate', data, { timeout: 180000 })
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

// 后台：每日记忆
export function sandboxMemories(params) {
  return request.get('/admin/sandbox/memories', { params })
}

export function saveSandboxMemory(data) {
  return request.post('/admin/sandbox/memories', data)
}

export function deleteSandboxMemory(id) {
  return request.delete(`/admin/sandbox/memories/${id}`)
}

// 后台：立即生成当天记忆（调试用）
export function summarizeSandboxMemories() {
  return request.post('/admin/sandbox/memories/summarize', null, { timeout: 600000 })
}

// 后台：角色背包
export function sandboxItems(characterId) {
  return request.get('/admin/sandbox/items', { params: { characterId } })
}

export function saveSandboxItem(data) {
  return request.post('/admin/sandbox/items', data)
}

export function deleteSandboxItem(id) {
  return request.delete(`/admin/sandbox/items/${id}`)
}

// 后台：旅人纪闻
export function sandboxNewsList(params) {
  return request.get('/admin/sandbox/news', { params })
}

export function saveSandboxNews(data) {
  return request.post('/admin/sandbox/news', data)
}

export function deleteSandboxNews(id) {
  return request.delete(`/admin/sandbox/news/${id}`)
}

// 由 AI 生成若干条当天事件（耗时长一些）
export function generateSandboxNews(data) {
  return request.post('/admin/sandbox/news/generate', data, { timeout: 180000 })
}
