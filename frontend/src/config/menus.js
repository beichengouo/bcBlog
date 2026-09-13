// 后台菜单统一定义，供侧边栏和“管理员管理”页面共用。
// 带 children 的项会渲染成“父菜单 + 多个子菜单”，children 里才是真正可授权的菜单项。
export const adminMenus = [
  { key: 'dashboard', path: '/admin/dashboard', title: '仪表盘', icon: 'Odometer' },
  { key: 'articles', path: '/admin/articles', title: '文章管理', icon: 'Document' },
  { key: 'categories', path: '/admin/categories', title: '分类管理', icon: 'Menu' },
  { key: 'tags', path: '/admin/tags', title: '标签管理', icon: 'CollectionTag' },
  { key: 'comments', path: '/admin/comments', title: '评论管理', icon: 'ChatDotRound' },
  { key: 'logs', path: '/admin/logs', title: '登录日志', icon: 'List' },
  {
    key: 'api',
    title: 'API 管理',
    icon: 'Connection',
    children: [
      { key: 'deepseek', path: '/admin/deepseek', title: 'DeepSeek 接口', icon: 'Wallet' },
      { key: 'ai', path: '/admin/ai', title: 'AI 服务商', icon: 'MagicStick' },
      { key: 'music', path: '/admin/music', title: '网易云音乐', icon: 'Headset' },
      { key: 'third', path: '/admin/third', title: '第三方接口', icon: 'Link' }
    ]
  },
  { key: 'live2d', path: '/admin/live2d', title: '看板娘管理', icon: 'User' },
  { key: 'background', path: '/admin/background', title: '背景管理', icon: 'Picture' },
  { key: 'announcement', path: '/admin/announcement', title: '站点公告', icon: 'Bell' },
  { key: 'settings', path: '/admin/settings', title: '系统设置', icon: 'Setting' },
  { key: 'admins', path: '/admin/admins', title: '管理员管理', icon: 'UserFilled', superOnly: true }
]
