<template>
  <div class="auth-page">
    <div class="aurora aurora-1"></div>
    <div class="aurora aurora-2"></div>

    <div class="auth-card glass">
      <div class="brand">
        <img class="brand-logo" :src="siteLogo" alt="Logo" />
        <h1 class="brand-name">{{ siteName }}</h1>
        <p class="brand-sub">欢迎回来</p>
      </div>

      <el-tabs v-model="tab" class="auth-tabs" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" class="auth-form" @submit.prevent>
            <el-form-item>
              <el-input v-model="loginForm.account" size="large" placeholder="邮箱 / 管理员用户名" clearable />
            </el-form-item>
            <el-form-item>
              <el-input v-model="loginForm.password" size="large" type="password" show-password placeholder="密码" @keyup.enter="onLogin" />
            </el-form-item>
            <el-button class="submit-btn" size="large" :loading="loading" @click="onLogin">登录</el-button>
          </el-form>
          <p class="switch-tip">还没有账号？<a @click="tab = 'register'">立即注册</a></p>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" class="auth-form" @submit.prevent>
            <el-form-item>
              <el-input v-model="registerForm.email" size="large" placeholder="邮箱" clearable />
            </el-form-item>
            <el-form-item>
              <div class="code-row">
                <el-input v-model="registerForm.emailCode" size="large" placeholder="邮箱验证码" />
                <el-button size="large" :disabled="codeCountdown > 0" @click="onSendCode">
                  {{ codeCountdown > 0 ? codeCountdown + 's' : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item>
              <el-input v-model="registerForm.nickname" size="large" placeholder="昵称（可选）" clearable />
            </el-form-item>
            <el-form-item>
              <el-input v-model="registerForm.password" size="large" type="password" show-password placeholder="密码（至少 8 位，含字母和数字）" />
            </el-form-item>
            <el-form-item>
              <el-input v-model="registerForm.confirmPassword" size="large" type="password" show-password placeholder="确认密码" />
            </el-form-item>
            <el-form-item v-if="inviteRequired">
              <el-input v-model="registerForm.inviteCode" size="large" placeholder="邀请码（必填）" clearable />
            </el-form-item>
            <el-button class="submit-btn" size="large" :loading="loading" @click="onRegister">注册</el-button>
          </el-form>
          <p class="switch-tip">已有账号？<a @click="tab = 'login'">去登录</a></p>
        </el-tab-pane>
      </el-tabs>

      <router-link class="back-home" to="/portal">返回首页</router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useMemberStore } from '@/store/member'
import { useThemeStore } from '@/store/theme'
import { getPortalConfig } from '@/api/config'
import { sendEmailCode } from '@/api/member'

const router = useRouter()
const route = useRoute()
const memberStore = useMemberStore()
const themeStore = useThemeStore()

const tab = ref('login')
const loading = ref(false)
const siteName = ref('bcBlog')
const siteLogo = ref('/uploads/logo/avatar.png')
const inviteRequired = ref(false)
const codeCountdown = ref(0)
let timer = 0

const loginForm = reactive({ account: '', password: '' })
const registerForm = reactive({
  email: '',
  emailCode: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  inviteCode: ''
})

async function onSendCode() {
  if (!registerForm.email.trim()) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  try {
    const res = await sendEmailCode(registerForm.email.trim())
    if (res && res.sent) {
      ElMessage.success('验证码已发送到邮箱，请查收')
    } else {
      registerForm.emailCode = res.code || ''
      ElMessage.success(`测试验证码：${res.code}，已自动填入`)
    }
    codeCountdown.value = 60
    clearInterval(timer)
    timer = setInterval(() => {
      codeCountdown.value--
      if (codeCountdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (e) {
    // 错误提示由请求层统一处理
  }
}

async function onLogin() {
  if (!loginForm.account.trim() || !loginForm.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await memberStore.login({ account: loginForm.account.trim(), password: loginForm.password })
    ElMessage.success('登录成功')
    router.push('/portal/user')
  } finally {
    loading.value = false
  }
}

async function onRegister() {
  if (!registerForm.email.trim() || !registerForm.password) {
    ElMessage.warning('请填写邮箱和密码')
    return
  }
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(registerForm.password)) {
    ElMessage.warning('密码至少 8 位，且必须同时包含字母和数字')
    return
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (inviteRequired.value && !registerForm.inviteCode.trim()) {
    ElMessage.warning('请输入邀请码')
    return
  }
  loading.value = true
  try {
    await memberStore.register({ ...registerForm })
    ElMessage.success('注册成功，已自动登录')
    router.push('/portal/user')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  themeStore.apply()
  if (route.query.tab === 'register') {
    tab.value = 'register'
  }
  try {
    const config = await getPortalConfig()
    siteName.value = config.siteName || 'bcBlog'
    if (config.siteLogo) siteLogo.value = config.siteLogo
    inviteRequired.value = config.registerInviteRequired === 1
  } catch (e) {
    // 使用默认站点信息
  }
})

onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.auth-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--bg);
  padding: 24px;
}
.aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.5;
  pointer-events: none;
}
.aurora-1 {
  width: 440px;
  height: 440px;
  background: var(--accent);
  top: -140px;
  left: -120px;
}
.aurora-2 {
  width: 420px;
  height: 420px;
  background: var(--accent-2);
  bottom: -150px;
  right: -120px;
}
.auth-card {
  position: relative;
  z-index: 2;
  width: min(94vw, 440px);
  padding: 30px 30px 22px;
  border-radius: 22px;
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  backdrop-filter: blur(18px) saturate(1.3);
  -webkit-backdrop-filter: blur(18px) saturate(1.3);
}
.brand {
  text-align: center;
  margin-bottom: 12px;
}
.brand-logo {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--border);
  box-shadow: var(--shadow);
}
.brand-name {
  margin: 12px 0 2px;
  font-size: 22px;
  font-weight: 800;
  background: linear-gradient(120deg, var(--accent), var(--accent-2));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.brand-sub {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 2px;
}
.auth-tabs :deep(.el-tabs__item) {
  color: var(--text-muted);
}
.auth-tabs :deep(.el-tabs__item.is-active) {
  color: var(--accent);
}
.auth-form :deep(.el-input__wrapper) {
  background: var(--glass-bg);
  border-radius: 12px;
  box-shadow: 0 0 0 1px var(--border) inset;
}
.auth-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--accent) inset, 0 0 12px var(--accent-soft);
}
.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.submit-btn {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 12px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  box-shadow: 0 10px 24px var(--accent-soft);
}
.switch-tip {
  margin: 14px 0 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}
.switch-tip a {
  color: var(--accent);
  cursor: pointer;
}
.back-home {
  display: block;
  margin-top: 16px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
  text-decoration: none;
}
</style>
