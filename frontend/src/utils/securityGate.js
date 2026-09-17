import { ElMessage, ElMessageBox } from 'element-plus'
import { securityStatus, securityVerify } from '@/api/security'

/**
 * 后台敏感菜单的二次验证门禁。
 *
 * 规则（按本次登录算）：
 *   1. 每个受保护的菜单，第一次进入时必须输入安全密码；
 *      验证通过后该菜单在本次登录内不再重复要求（换菜单仍需验证）。
 *   2. 弹窗不能点空白处 / ESC / 右上角关闭，必须显式点「验证」或「取消」。
 *   3. 取消验证 = 不进入该菜单（路由直接中止），页面不会再去加载数据。
 *   4. 刷新页面后记录清空，需要重新验证。
 */

// 本次登录已解锁的菜单 key（放在 sessionStorage：关掉标签页即失效）
const UNLOCK_KEY = 'secUnlockedMenus'

function unlockedMenus() {
  try {
    return JSON.parse(sessionStorage.getItem(UNLOCK_KEY) || '[]')
  } catch (e) {
    return []
  }
}

function markUnlocked(menuKey) {
  const list = unlockedMenus()
  if (!list.includes(menuKey)) {
    list.push(menuKey)
    sessionStorage.setItem(UNLOCK_KEY, JSON.stringify(list))
  }
}

/** 退出登录 / 锁定后台时清空已解锁记录 */
export function clearSecurityUnlock() {
  sessionStorage.removeItem(UNLOCK_KEY)
}

/**
 * 进入受保护菜单前的校验。
 * @returns {Promise<boolean>} true = 允许进入；false = 用户取消或未设置安全密码
 */
export async function ensureSecurityVerified(menuKey, menuTitle) {
  if (unlockedMenus().includes(menuKey)) {
    return true
  }

  let status = null
  try {
    status = await securityStatus()
  } catch (e) {
    return false
  }
  // 后台「登录保护 → 二次验证总开关」关掉时，前端也必须放行：
  // 这个开关是后端 WebConfig 的 admin_security_enabled，两边行为必须一致，
  // 否则就会出现"开关关了、点菜单还在要密码"（这正是之前报的那个 bug）
  if (status && status.enabled !== '1') {
    return true
  }
  if (status && status.hasSecurityPassword === false) {
    ElMessage.error('还没有设置安全密码，请先到「系统安全 → 安全设置」设置')
    return false
  }

  // 密码输错时不退出弹窗，让用户重试；点「取消」才中止
  // eslint-disable-next-line no-constant-condition
  while (true) {
    let value = ''
    try {
      const res = await ElMessageBox.prompt(
        `进入「${menuTitle || '该菜单'}」需要二次验证`,
        '安全验证',
        {
          confirmButtonText: '验证',
          cancelButtonText: '取消',
          inputType: 'password',
          inputPlaceholder: '请输入安全密码',
          // 关键：不允许通过点空白处、ESC、右上角关闭来"跳过"验证
          closeOnClickModal: false,
          closeOnPressEscape: false,
          showClose: false,
          inputValidator: (v) => (v && v.trim() ? true : '请输入安全密码')
        }
      )
      value = res.value
    } catch (e) {
      // 用户点了取消
      return false
    }
    try {
      await securityVerify(value)
      markUnlocked(menuKey)
      ElMessage.success('验证通过')
      return true
    } catch (e) {
      // 密码不正确：后端已提示，这里继续弹窗让用户重试
    }
  }
}
