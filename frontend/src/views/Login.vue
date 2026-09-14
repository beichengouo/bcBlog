<template>
  <div class="login-page">
    <!-- 背景光斑 -->
    <div class="aurora aurora-1"></div>
    <div class="aurora aurora-2"></div>
    <div class="aurora aurora-3"></div>
    <div class="stars">
      <i v-for="n in 36" :key="n" :style="starStyle(n)"></i>
    </div>

    <!-- 主题切换 -->
    <button class="theme-btn" @click="themeStore.toggle()" :title="themeStore.isDark ? '切换到白天' : '切换到夜晚'">
      <svg v-if="themeStore.isDark" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="5" />
        <path d="M12 1v2M12 21v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M1 12h2M21 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4" />
      </svg>
      <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
      </svg>
    </button>

    <div class="login-card glass">
      <div class="card-glow"></div>
      <div class="brand">
        <img class="brand-logo" :src="siteLogo" alt="Logo" />
        <h1 class="brand-name">{{ siteName }}</h1>
        <p class="brand-sub">Administrator Console</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" class="login-form" @submit.prevent>
        <el-form-item prop="username">
          <el-input v-model="form.username" size="large" placeholder="用户名" clearable>
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            placeholder="密码"
            @keyup.enter="onSubmit"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" size="large" placeholder="验证码" @keyup.enter="onSubmit" />
            <img :src="captchaImage" class="captcha-img" title="点击刷新" alt="验证码" @click="refreshCaptcha" />
          </div>
        </el-form-item>
        <el-button class="submit-btn" size="large" :loading="loading" @click="onSubmit">
          进入后台
        </el-button>
      </el-form>

      <p class="footer-tip">Powered by bcBlog · 愿每一次点击都有温度</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha } from '@/api/auth'
import { getPortalConfig } from '@/api/config'
import { useUserStore } from '@/store/user'
import { useThemeStore } from '@/store/theme'

const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()

const formRef = ref()
const loading = ref(false)
const captchaImage = ref('')
const siteName = ref('bcBlog')
const siteLogo = ref('/uploads/logo/avatar.png')

const form = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

/** 生成星星的随机位置和动画延迟 */
function starStyle(n) {
  const seed = (n * 9301 + 49297) % 233280
  const rnd = seed / 233280
  const left = ((n * 37) % 100)
  const top = ((n * 53) % 100)
  const delay = (rnd * 4).toFixed(2)
  const size = (rnd * 2 + 1).toFixed(1)
  return { left: left + '%', top: top + '%', animationDelay: delay + 's', width: size + 'px', height: size + 'px' }
}

async function refreshCaptcha() {
  const data = await getCaptcha()
  form.captchaId = data.captchaId
  captchaImage.value = data.image
  form.captchaCode = ''
}

async function onSubmit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    await userStore.login({ ...form })
    ElMessage.success('登录成功')
    router.push('/admin/dashboard')
  } catch (e) {
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  themeStore.apply()
  refreshCaptcha()
  try {
    const config = await getPortalConfig()
    siteName.value = config.siteName || 'bcBlog'
    if (config.siteLogo) {
      siteLogo.value = config.siteLogo
    }
  } catch (e) {
    // 使用默认站点信息
  }
})
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--bg);
}
.aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.55;
  pointer-events: none;
}
.aurora-1 {
  width: 460px;
  height: 460px;
  background: var(--accent);
  top: -140px;
  left: -120px;
  animation: drift1 16s ease-in-out infinite;
}
.aurora-2 {
  width: 420px;
  height: 420px;
  background: var(--accent-2);
  bottom: -150px;
  right: -120px;
  animation: drift2 18s ease-in-out infinite;
}
.aurora-3 {
  width: 320px;
  height: 320px;
  background: var(--accent);
  bottom: 10%;
  left: 12%;
  opacity: 0.3;
  animation: drift1 22s ease-in-out infinite reverse;
}
@keyframes drift1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(60px, 40px) scale(1.12); }
}
@keyframes drift2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(-50px, -40px) scale(1.08); }
}
.stars {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.stars i {
  position: absolute;
  border-radius: 50%;
  background: #fff;
  opacity: 0.7;
  animation: twinkle 3.5s ease-in-out infinite;
}
@keyframes twinkle {
  0%, 100% { opacity: 0.15; transform: scale(0.8); }
  50% { opacity: 0.9; transform: scale(1.15); }
}
.theme-btn {
  position: absolute;
  top: 22px;
  right: 24px;
  z-index: 3;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: var(--glass-bg);
  color: var(--text);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(12px);
  transition: transform 0.3s ease;
}
.theme-btn:hover {
  transform: rotate(20deg) scale(1.06);
}
.login-card {
  position: relative;
  z-index: 2;
  width: min(92vw, 420px);
  padding: 38px 34px 26px;
  border-radius: 22px;
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  backdrop-filter: blur(18px) saturate(1.3);
  -webkit-backdrop-filter: blur(18px) saturate(1.3);
  overflow: hidden;
}
.card-glow {
  position: absolute;
  top: -60px;
  right: -60px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: var(--accent-soft);
  filter: blur(10px);
  pointer-events: none;
}
.brand {
  text-align: center;
  margin-bottom: 24px;
}
.brand-logo {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: var(--shadow);
  border: 2px solid var(--border);
}
.brand-name {
  margin: 14px 0 4px;
  font-size: 24px;
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
  letter-spacing: 3px;
  text-transform: uppercase;
}
.login-form :deep(.el-input__wrapper) {
  background: var(--glass-bg);
  border-radius: 12px;
  box-shadow: 0 0 0 1px var(--border) inset;
}
.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--accent) inset, 0 0 12px var(--accent-soft);
}
.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.captcha-img {
  height: 40px;
  border-radius: 10px;
  cursor: pointer;
  border: 1px solid var(--border);
}
.submit-btn {
  width: 100%;
  height: 46px;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  box-shadow: 0 10px 24px var(--accent-soft);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px var(--accent-soft);
}
.footer-tip {
  margin: 20px 0 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}
@media (max-width: 480px) {
  .login-card {
    padding: 28px 20px 20px;
  }
  .brand-logo {
    width: 54px;
    height: 54px;
  }
}
</style>
