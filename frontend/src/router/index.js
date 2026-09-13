import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  { path: '/', redirect: '/portal' },
  { path: '/portal', component: () => import('@/views/portal/Home.vue') },
  { path: '/login', component: () => import('@/views/Login.vue') },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '仪表盘' } },
      { path: 'articles', component: () => import('@/views/admin/ArticleList.vue'), meta: { title: '文章管理' } },
      { path: 'articles/edit', component: () => import('@/views/admin/ArticleEdit.vue'), meta: { title: '新增文章' } },
      { path: 'articles/edit/:id', component: () => import('@/views/admin/ArticleEdit.vue'), meta: { title: '编辑文章' } },
      { path: 'categories', component: () => import('@/views/admin/CategoryManage.vue'), meta: { title: '分类管理' } },
      { path: 'tags', component: () => import('@/views/admin/TagManage.vue'), meta: { title: '标签管理' } },
      { path: 'comments', component: () => import('@/views/admin/Placeholder.vue'), meta: { title: '评论管理' } },
      { path: 'settings', component: () => import('@/views/admin/Placeholder.vue'), meta: { title: '系统设置' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.path.startsWith('/admin') && !userStore.token) {
    return '/login'
  }
})

export default router
