const inFlight = new Map()
const CACHE_KEY = 'bcblog-daily-hitokoto-v1'

export function shanghaiDay(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now)
  const part = (type) => parts.find((item) => item.type === type).value
  return `${part('year')}-${part('month')}-${part('day')}`
}

export function nextQuoteRefresh(now = new Date()) {
  return Date.parse(`${shanghaiDay(now)}T00:00:00+08:00`) + 86400000 - now.getTime() + 1000
}

export async function fetchHitokoto(categories = 'd,i,k') {
  const query = new URLSearchParams()
  String(categories).split(',').map((c) => c.trim()).filter((c) => /^[a-l]$/.test(c)).forEach((c) => query.append('c', c))
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 8000)
  try {
    const response = await fetch(`https://v1.hitokoto.cn/?${query}`, { signal: controller.signal })
    if (!response.ok) throw new Error('一言接口暂不可用')
    const data = await response.json()
    if (typeof data.hitokoto !== 'string' || !data.hitokoto.trim()) throw new Error('一言内容为空')
    return { text: data.hitokoto.trim(), source: [data.from, data.from_who].filter((s) => typeof s === 'string' && s.trim()).join(' · ') || '来源未标注' }
  } finally {
    clearTimeout(timer)
  }
}

export function getDailyQuote(categories = 'd,i,k', now = new Date()) {
  const day = shanghaiDay(now)
  const categoryKey = String(categories || 'd,i,k')
  const key = `${day}:${categoryKey}`
  if (inFlight.has(key)) return inFlight.get(key)
  const task = (async () => {
    let cached
    try { cached = JSON.parse(localStorage.getItem(CACHE_KEY) || 'null') } catch { /* Storage can be unavailable. */ }
    const valid = cached && typeof cached.text === 'string' && cached.text.trim() && typeof cached.source === 'string'
    if (valid && cached.day === day && cached.categories === categoryKey) return { ...cached, stale: false }
    try {
      const quote = { ...await fetchHitokoto(categoryKey), day, categories: categoryKey, stale: false }
      try { localStorage.setItem(CACHE_KEY, JSON.stringify(quote)) } catch { /* Keep the fetched quote even if saving fails. */ }
      return quote
    } catch {
      if (valid) return { ...cached, stale: true }
      return { text: '愿每一次点击都有温度。', source: '小站寄语（离线备用）', day, categories: categoryKey, stale: true }
    }
  })()
  inFlight.set(key, task)
  task.finally(() => inFlight.delete(key))
  return task
}
