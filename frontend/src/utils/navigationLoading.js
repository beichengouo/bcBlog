import { reactive } from 'vue'
export const navigationLoading = reactive({ visible: false, done: false, error: false, retryPath: '' })
let activeTarget, revealTimer, finishTimer
function clearTimers() { clearTimeout(revealTimer); clearTimeout(finishTimer) }
export function startNavigation(target) {
  clearTimers(); activeTarget = target
  navigationLoading.done = false; navigationLoading.error = false; navigationLoading.retryPath = ''
  revealTimer = setTimeout(() => { navigationLoading.visible = true }, 160)
}
export function finishNavigation(target, failed = false) {
  if (!target || target !== activeTarget) return
  clearTimers(); activeTarget = null
  navigationLoading.done = !failed
  finishTimer = setTimeout(() => { navigationLoading.visible = false; navigationLoading.done = false }, failed ? 0 : 200)
}
export function failNavigation(target) {
  if (!target || target !== activeTarget) return
  clearTimers(); activeTarget = null
  navigationLoading.visible = false; navigationLoading.done = false; navigationLoading.error = true; navigationLoading.retryPath = target.fullPath
}
