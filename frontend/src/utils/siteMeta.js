import { getPortalConfig } from '@/api/config'

let favicon = null
let siteName = 'bcBlog'

/** 更新或创建页面的 favicon 标签。 */
function setFavicon(url) {
  if (!favicon) {
    favicon = document.querySelector('link[rel="icon"]')
  }
  if (!favicon) {
    favicon = document.createElement('link')
    favicon.rel = 'icon'
    document.head.appendChild(favicon)
  }
  favicon.href = url || '/uploads/logo/avatar.png'
}

/**
 * 根据站点配置同步浏览器标签页名称和图标。
 * 传入 config 可避免重复请求；不传时自动拉取前台站点配置。
 */
export async function applySiteMeta(config) {
  let conf = config
  if (!conf) {
    try {
      conf = await getPortalConfig()
    } catch (e) {
      conf = {}
    }
  }
  const name = conf.siteName || 'bcBlog'
  siteName = name
  document.title = name
  setFavicon(conf.siteLogo)
  return conf
}

/** 获取最近一次同步的站点名称，供路由标题拼接使用。 */
export function getSiteName() {
  return siteName
}
