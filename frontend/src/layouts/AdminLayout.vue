<template>
  <el-container class="admin-container">
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
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <template #title><span>{{ m.title }}</span></template>
        </el-menu-item>
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
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
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
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { changePassword } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

const isMobile = ref(false)
const collapsed = ref(false)
const drawerVisible = ref(false)

// 侧边栏菜单配置
const menus = [
  { path: '/admin/dashboard', title: '仪表盘', icon: 'Odometer' },
  { path: '/admin/articles', title: '文章管理', icon: 'Document' },
  { path: '/admin/categories', title: '分类管理', icon: 'Menu' },
  { path: '/admin/tags', title: '标签管理', icon: 'CollectionTag' },
  { path: '/admin/comments', title: '评论管理', icon: 'ChatDotRound' },
  { path: '/admin/logs', title: '登录日志', icon: 'List' },
  { path: '/admin/deepseek', title: 'DeepSeek 余额', icon: 'Wallet' },
  { path: '/admin/settings', title: '系统设置', icon: 'Setting' }
]

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
}
.aside {
  background: #001529;
  transition: width 0.2s;
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
  background: #fff;
  padding: 0 16px;
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
  background: #f0f2f5;
  padding: 16px;
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
