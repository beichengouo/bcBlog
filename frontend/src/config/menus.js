// 后台菜单统一定义，供侧边栏和“管理员管理”页面共用。
// 结构为“一级菜单 + 二级菜单”：一级菜单是分组，二级菜单才是真正可访问、可授权的页面。
export const adminMenus = [
  { key: 'dashboard', path: '/admin/dashboard', title: '仪表盘', icon: 'Odometer' },
  {
    key: 'content',
    title: '内容管理',
    icon: 'Document',
    children: [
      { key: 'articles', path: '/admin/articles', title: '文章管理', icon: 'Document' },
      { key: 'categories', path: '/admin/categories', title: '分类管理', icon: 'Menu' },
      { key: 'tags', path: '/admin/tags', title: '标签管理', icon: 'CollectionTag' },
      { key: 'comments', path: '/admin/comments', title: '评论管理', icon: 'ChatDotRound' },
      { key: 'gitalk', path: '/admin/gitalk', title: 'Gitalk 评论', icon: 'ChatLineSquare' }
    ]
  },
  {
    key: 'site',
    title: '站点管理',
    icon: 'Setting',
    children: [
      { key: 'settings', path: '/admin/settings', title: '系统设置', icon: 'Setting' },
      { key: 'announcement', path: '/admin/announcement', title: '站点公告', icon: 'Bell' },
      { key: 'emoji', path: '/admin/emoji', title: '表情包管理', icon: 'Star' },
      { key: 'photos', path: '/admin/photos', title: '流光忆庭', icon: 'PictureFilled' },
      { key: 'resources', path: '/admin/resources', title: '智库', icon: 'Collection' },
      { key: 'sandboxWorld', path: '/admin/sandbox/world', title: '沙盒世界', icon: 'MapLocation' },
      { key: 'sandboxCharacters', path: '/admin/sandbox/characters', title: '沙盒角色', icon: 'MagicStick' },
      { key: 'sandboxActs', path: '/admin/sandbox/acts', title: '沙盒日志', icon: 'Clock' },
      { key: 'background', path: '/admin/background', title: '背景管理', icon: 'Picture' },
      { key: 'live2d', path: '/admin/live2d', title: '看板娘管理', icon: 'User' }
    ]
  },
  {
    key: 'api',
    title: '接口管理',
    icon: 'Connection',
    children: [
      { key: 'deepseek', path: '/admin/deepseek', title: 'DeepSeek 接口', icon: 'Wallet' },
      { key: 'ai', path: '/admin/ai', title: 'AI 服务商', icon: 'MagicStick' },
      { key: 'music', path: '/admin/music', title: '网易云音乐', icon: 'Headset' },
      { key: 'third', path: '/admin/third', title: '第三方接口', icon: 'Link' },
      { key: 'email', path: '/admin/email', title: '邮件管理', icon: 'Message' }
    ]
  },
  {
    key: 'system',
    title: '系统安全',
    icon: 'Lock',
    children: [
      { key: 'logs', path: '/admin/logs', title: '登录日志', icon: 'List' },
      { key: 'points', path: '/admin/points', title: '积分管理', icon: 'Coin' },
      { key: 'admins', path: '/admin/admins', title: '管理员管理', icon: 'UserFilled', superOnly: true },
      { key: 'members', path: '/admin/members', title: '用户管理', icon: 'User', superOnly: true },
      { key: 'invites', path: '/admin/invites', title: '邀请码管理', icon: 'Ticket', superOnly: true },
      { key: 'levels', path: '/admin/levels', title: '等级配置', icon: 'Medal' }
    ]
  }
]
