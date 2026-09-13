<template>
  <el-container class="admin-container">
    <!-- 管理后台背景壁纸 -->
    <BackgroundLayer scope="admin" />
    <!-- 桌面端侧边栏 -->
    <el-aside v-if="!isMobile" :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo">{{ collapsed ? 'B' : 'bcBlog 后台' }}</div>
      <el-menu
        :default-active="$route.path"
        router
        :collapse="collapsed"
        background-color="#001529"
        text-color="#ffffffb3"
        active-text-color="#409eff"
      >
        <template v-for="m in visibleMenus" :key="m.key">
          <!-- API 管理这类带 children 的菜单，渲染为父菜单 + 子菜单 -->
          <el-sub-menu v-if="m.children" :index="m.key">
            <template #title>
              <el-icon><component :is="m.icon" /></el-icon>
              <span>{{ m.title }}</span>
            </template>
            <el-menu-item v-for="c in m.children" :key="c.path" :index="c.path">
              <el-icon><component :is="c.icon" /></el-icon>
              <template #title><span>{{ c.title }}</span></template>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="m.path">
            <el-icon><component :is="m.icon" /></el-icon>
            <template #title><span>{{ m.title }}</span></template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <!-- 移动端抽屉菜单 -->
    <el-drawer
      v-if="isMobile"
      v-model="drawerVisible"
      direction="ltr"
      size="220px"
      :with-header="false"
      class="mobile-drawer"
    >
      <div class="logo">bcBlog 后台</div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#001529"
        text-color="#ffffffb3"
        active-text-color="#409eff"
        @select="drawerVisible = false"
      >
        <template v-for="m in visibleMenus" :key="m.key">
          <el-sub-menu v-if="m.children" :index="m.key">
            <template #title>
              <el-icon><component :is="m.icon" /></el-icon>
              <span>{{ m.title }}</span>
            </template>
            <el-menu-item v-for="c in m.children" :key="c.path" :index="c.path" @click="drawerVisible = false">
              <el-icon><component :is="c.icon" /></el-icon>
              <span>{{ c.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="m.path">
            <el-icon><component :is="m.icon" /></el-icon>
            <span>{{ m.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-drawer>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleSidebar">
            <component :is="isMobile ? 'Expand' : collapsed ? 'Expand' : 'Fold'" />
          </el-icon>
          <div class="header-title">{{ $route.meta.title || '后台管理' }}</div>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="onCommand">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '管理员' }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="min(92vw, 480px)">
      <el-form :model="pwdForm" :rules="pwdRules" ref="pwdFormRef" label-width="110px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="onChangePassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { changePassword } from '@/api/auth'
import BackgroundLayer from '@/components/portal/BackgroundLayer.vue'
import { adminMenus } from '@/config/menus'

const router = useRouter()
const userStore = useUserStore()

const isMobile = ref(false)
const collapsed = ref(false)
const drawerVisible = ref(false)

// 侧边栏菜单：超级管理员显示全部，普通管理员只显示被授权的菜单。
// 带 children 的父菜单，只要有任意一个子菜单被授权，就显示该父菜单。
const visibleMenus = computed(() => {
  const info = userStore.userInfo
  if (!info || info.role === 'SUPER') {
    return adminMenus
  }
  const keys = info.menus || []
  return adminMenus
    .map((m) => {
      if (m.children) {
        const children = m.children.filter((c) => keys.includes(c.key))
        return children.length ? { ...m, children } : null
      }
      if (m.superOnly) return null
      if (m.key === 'dashboard' || keys.includes(m.key)) return m
      return null
    })
    .filter(Boolean)
})

const pwdVisible = ref(false)
const pwdFormRef = ref()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '新密码至少 8 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function checkWidth() {
  isMobile.value = window.innerWidth < 768
}

function toggleSidebar() {
  if (isMobile.value) {
    drawerVisible.value = true
  } else {
    collapsed.value = !collapsed.value
  }
}

function onCommand(cmd) {
  if (cmd === 'password') {
    pwdVisible.value = true
  } else if (cmd === 'logout') {
    onLogout()
  }
}

async function onChangePassword() {
  try {
    await pwdFormRef.value.validate()
  } catch (e) {
    return
  }
  await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
  ElMessage.success('密码修改成功，请重新登录')
  pwdVisible.value = false
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  await userStore.logout()
  router.push('/login')
}

async function onLogout() {
  await userStore.logout()
  router.push('/login')
}

onMounted(() => {
  userStore.fetchInfo().catch(() => {})
  checkWidth()
  window.addEventListener('resize', checkWidth)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkWidth)
})
</script>

<style scoped>
.admin-container {
  height: 100vh;
  position: relative;
  z-index: 1;
}
.aside {
  background: rgba(0, 21, 41, 0.9);
  transition: width 0.2s;
  position: relative;
  z-index: 1;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
}
.aside :deep(.el-menu) {
  border-right: none;
}
.mobile-drawer :deep(.el-drawer__body) {
  padding: 0;
  background: #001529;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #eee;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  padding: 0 16px;
  position: relative;
  z-index: 1;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
}
.header-title {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  outline: none;
}
.main {
  background: rgba(244, 246, 250, 0.72);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  padding: 16px;
  position: relative;
  z-index: 1;
}
@media (max-width: 768px) {
  .main {
    padding: 10px;
  }
  .header {
    padding: 0 10px;
  }
}
</style>
