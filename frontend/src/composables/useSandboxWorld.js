import { ref } from 'vue'

/**
 * 后台沙盒页共用的「当前世界」选择。
 * 「世界与地图 / 角色管理 / 行动日志」三个页面共享同一个值（存在浏览器里），
 * 切一次就能在三个页面之间保持一致，不用每个页面单独切。
 */
const STORAGE_KEY = 'bcblog-admin-sandbox-world'

const stored = Number(localStorage.getItem(STORAGE_KEY))
const currentWorldId = ref(Number.isFinite(stored) && stored > 0 ? stored : null)

export function useSandboxWorld() {
  function setCurrentWorld(id) {
    currentWorldId.value = id == null ? null : Number(id)
    if (id == null) {
      localStorage.removeItem(STORAGE_KEY)
    } else {
      localStorage.setItem(STORAGE_KEY, String(id))
    }
  }

  return { currentWorldId, setCurrentWorld }
}
