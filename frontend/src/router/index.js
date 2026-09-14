import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'
import { applySiteMeta, getSiteName } from '@/utils/siteMeta'

const routes = [
  { path: '/', redirect: '/portal' },
  {
    path: '/portal',
    component: () => import('@/layouts/PortalLayout.vue'),
    children: [
      { path: '', component: () => import('@/views/portal/Home.vue') },
      { path: 'article/:id', component: () => import('@/views/portal/ArticleDetail.vue') },
      { path: 'photos', component: () => import('@/views/portal/Photos.vue') },
      { path: 'resources', component: () => import('@/views/portal/Resources.vue') },
      { path: 'resources/:id', component: () => import('@/views/portal/ResourceDetail.vue') },
      { path: 'login', component: () => import('@/views/portal/UserAuth.vue') },
      { path: 'user', component: () => import('@/views/portal/UserCenter.vue') },
      { path: 'announcements', component: () => import('@/views/portal/Announcements.vue') }
    ]
  },
  { path: '/login', component: () => import('@/views/Login.vue') },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '仪表盘', menu: 'dashboard' } },
      { path: 'articles', component: () => import('@/views/admin/ArticleList.vue'), meta: { title: '文章管理', menu: 'articles' } },
      { path: 'articles/edit', component: () => import('@/views/admin/ArticleEdit.vue'), meta: { title: '新增文章', menu: 'articles' } },
      { path: 'articles/edit/:id', component: () => import('@/views/admin/ArticleEdit.vue'), meta: { title: '编辑文章', menu: 'articles' } },
      { path: 'categories', component: () => import('@/views/admin/CategoryManage.vue'), meta: { title: '分类管理', menu: 'categories' } },
      { path: 'tags', component: () => import('@/views/admin/TagManage.vue'), meta: { title: '标签管理', menu: 'tags' } },
      { path: 'comments', component: () => import('@/views/admin/CommentManage.vue'), meta: { title: '评论管理', menu: 'comments' } },
      { path: 'gitalk', component: () => import('@/views/admin/GitalkManage.vue'), meta: { title: 'Gitalk 评论', menu: 'gitalk' } },
      { path: 'deepseek', component: () => import('@/views/admin/DeepseekBalance.vue'), meta: { title: 'DeepSeek 接口', menu: 'deepseek' } },
      { path: 'music', component: () => import('@/views/admin/MusicPlaylist.vue'), meta: { title: '网易云音乐', menu: 'music' } },
      { path: 'live2d', component: () => import('@/views/admin/Live2dModelManage.vue'), meta: { title: '看板娘管理', menu: 'live2d' } },
      { path: 'background', component: () => import('@/views/admin/BackgroundManage.vue'), meta: { title: '背景管理', menu: 'background' } },
      { path: 'ai', component: () => import('@/views/admin/AiProvider.vue'), meta: { title: 'AI 服务商', menu: 'ai' } },
      { path: 'third', component: () => import('@/views/admin/ApiThird.vue'), meta: { title: '第三方接口', menu: 'third' } },
      { path: 'email', component: () => import('@/views/admin/EmailManage.vue'), meta: { title: '邮件管理', menu: 'email' } },
      { path: 'announcement', component: () => import('@/views/admin/AnnouncementManage.vue'), meta: { title: '站点公告', menu: 'announcement' } },
      { path: 'emoji', component: () => import('@/views/admin/EmojiManage.vue'), meta: { title: '表情包管理', menu: 'emoji' } },
      { path: 'photos', component: () => import('@/views/admin/PhotoManage.vue'), meta: { title: '流光忆庭', menu: 'photos' } },
      { path: 'resources', component: () => import('@/views/admin/ResourceManage.vue'), meta: { title: '智库', menu: 'resources' } },
      { path: 'settings', component: () => import('@/views/admin/Settings.vue'), meta: { title: '系统设置', menu: 'settings' } },
      { path: 'admins', component: () => import('@/views/admin/AdminUserManage.vue'), meta: { title: '管理员管理', menu: 'admins', superOnly: true } },
      { path: 'members', component: () => import('@/views/admin/MemberManage.vue'), meta: { title: '用户管理', menu: 'members', superOnly: true } },
      { path: 'levels', component: () => import('@/views/admin/LevelManage.vue'), meta: { title: '等级配置', menu: 'levels' } },
      { path: 'invites', component: () => import('@/views/admin/InviteManage.vue'), meta: { title: '邀请码管理', menu: 'invites', superOnly: true } },
      { path: 'logs', component: () => import('@/views/admin/LoginLog.vue'), meta: { title: '登录日志', menu: 'logs' } },
      { path: 'points', component: () => import('@/views/admin/PointManage.vue'), meta: { title: '积分管理', menu: 'points' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  if (to.path.startsWith('/admin') && !userStore.token) {
    return '/login'
  }
  if (to.path.startsWith('/admin') && userStore.token) {
    if (!userStore.userInfo) {
      try {
        await userStore.fetchInfo()
      } catch (e) {
        userStore.clear()
        return '/login'
      }
    }
    const info = userStore.userInfo
    // 普通用户不能进入后台
    if (info.role === 'USER') {
      return '/portal'
    }
    // 仅超级管理员可访问的管理员管理页
    if (to.meta.superOnly && info.role !== 'SUPER') {
      return '/admin/dashboard'
    }
    // 普通管理员只能访问被授权的菜单（仪表盘始终可用）
    if (info.role !== 'SUPER' && to.meta.menu && to.meta.menu !== 'dashboard') {
      const keys = info.menus || []
      if (!keys.includes(to.meta.menu)) {
        return '/admin/dashboard'
      }
    }
  }
})

// 路由切换时同步站点标签页名称和 favicon
router.afterEach((to) => {
  // 保持 favicon 与最新站点 Logo 同步
  applySiteMeta().then(() => {
    if (to.meta && to.meta.title) {
      document.title = `${to.meta.title} - ${getSiteName()}`
    }
  }).catch(() => {})
})

export default router
