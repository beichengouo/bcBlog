// 前台：上报一次访问。
// 访问统计属于附加功能，失败时静默处理，不打扰访客。
export function reportVisit() {
  return fetch('/api/portal/visit/report', { method: 'POST' }).catch(() => {})
}
