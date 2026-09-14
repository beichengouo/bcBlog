<template>
  <div class="portal">
    <!-- 背景壁纸（图片或视频） -->
    <BackgroundLayer scope="portal" />
    <!-- 背景粒子（白天樱花 / 夜晚星空） -->
    <ParticleCanvas :active="showParticles" />
    <ClickRipple />
    <ReadingProgress />

    <header class="portal-header" :class="{ scrolled }">
      <div class="header-inner">
        <router-link to="/portal" class="brand">
          <img v-if="siteLogo" class="brand-logo" :src="siteLogo" alt="站点 Logo" />
          <span class="brand-text">{{ siteName }}</span>
        </router-link>

        <nav class="portal-nav">
          <router-link to="/portal/photos" class="nav-pill">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="4" width="18" height="16" rx="3" />
              <circle cx="9" cy="10" r="2" />
              <path d="m21 16-4.5-4.5L9 19" />
            </svg>
            <span>流光忆庭</span>
          </router-link>
          <router-link to="/portal/resources" class="nav-pill">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5z" />
              <path d="M4 5.5v15" />
              <path d="M9 7h7M9 11h5" />
            </svg>
            <span>智库</span>
          </router-link>
        </nav>

        <form class="search" @submit.prevent="onSearch">
          <input v-model="keyword" placeholder="搜索文章..." aria-label="搜索" />
          <button type="submit">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="7" />
              <path d="m21 21-4.3-4.3" />
            </svg>
          </button>
        </form>

        <button class="theme-toggle" @click="themeStore.toggle()" :aria-label="themeStore.isDark ? '切换到白天' : '切换到夜晚'">
          <svg v-if="themeStore.isDark" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="5" />
            <path d="M12 1v2M12 21v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M1 12h2M21 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4" />
          </svg>
          <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
          </svg>
        </button>
        <button
          class="theme-toggle"
          :class="{ on: showParticles }"
          @click="toggleParticles"
          :aria-label="showParticles ? '关闭樱花特效' : '开启樱花特效'"
          :title="showParticles ? '关闭樱花特效' : '开启樱花特效'"
        >
          <svg viewBox="0 0 24 24" width="19" height="19" fill="currentColor">
            <path d="M12 2c.6 4.8 2.4 6.6 7 7-4.6.6-6.4 2.4-7 7-.6-4.6-2.4-6.4-7-7 4.6-.4 6.4-2.2 7-7z" />
            <path d="M19 14c.3 2.3 1.1 3.1 3.4 3.4-2.3.3-3.1 1.1-3.4 3.4-.3-2.3-1.1-3.1-3.4-3.4 2.3-.3 3.1-1.1 3.4-3.4z" opacity=".7" />
          </svg>
        </button>
        <router-link class="theme-toggle announcement-link" to="/portal/announcements" title="站点公告" aria-label="站点公告">
          <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 9v6M2 10v4M6 8l3-4 3 4v9H6z" />
            <path d="M12 17h4a3 3 0 0 0 0-6h-1" />
            <path d="M15 11h1a3 3 0 0 1 0 6h-1" />
          </svg>
        </router-link>

        <!-- 用户入口 -->
        <div class="user-entry">
          <el-dropdown v-if="memberStore.isLogin" trigger="click" @command="onMemberCommand">
            <span class="member-info">
              <img v-if="memberStore.userInfo?.avatar" class="member-avatar" :src="memberStore.userInfo.avatar" alt="" />
              <span v-else class="member-avatar placeholder">{{ (memberStore.userInfo?.nickname || memberStore.userInfo?.username || 'U').slice(0, 1) }}</span>
              <span class="member-name">{{ memberStore.userInfo?.nickname || memberStore.userInfo?.username }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="center">用户中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <template v-else>
            <router-link class="user-link" to="/portal/login">登录</router-link>
            <router-link class="user-link primary" to="/portal/login?tab=register">注册</router-link>
          </template>
        </div>
      </div>
    </header>

    <main class="portal-main">
      <router-view />
    </main>

    <footer class="portal-footer">
      <div class="footer-inner">
        <div class="footer-brand">{{ siteName }}</div>
        <div class="footer-status">
          <span class="status-item">
            <span class="status-dot"></span>
            系统已稳定运行 {{ uptimeText }}
          </span>
          <span class="status-item status-time">{{ nowText }}</span>
        </div>
        <div class="footer-links">
          <span v-if="siteIcp" class="icp">{{ siteIcp }}</span>
          <router-link class="footer-link" to="/portal/photos">流光忆庭</router-link>
          <router-link class="footer-link" to="/portal/resources">智库</router-link>
          <span>Powered by bcBlog</span>
        </div>
      </div>
    </footer>

    <!-- 氛围组件 -->
    <Hitokoto />
    <MusicPlayer />
    <Live2DPet />
    <WeatherCard />
    <BackToTop />
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useThemeStore } from '@/store/theme'
import { useSiteStore } from '@/store/site'
import { applySiteMeta } from '@/utils/siteMeta'
import { getSystemInfo } from '@/api/system'
import { reportVisit } from '@/api/visit'
import { useMemberStore } from '@/store/member'
import BackgroundLayer from '@/components/portal/BackgroundLayer.vue'
import ParticleCanvas from '@/components/portal/ParticleCanvas.vue'
import ClickRipple from '@/components/portal/ClickRipple.vue'
import ReadingProgress from '@/components/portal/ReadingProgress.vue'
import Hitokoto from '@/components/portal/Hitokoto.vue'
import MusicPlayer from '@/components/portal/MusicPlayer.vue'
import Live2DPet from '@/components/portal/Live2DPet.vue'
import WeatherCard from '@/components/portal/WeatherCard.vue'
import BackToTop from '@/components/portal/BackToTop.vue'

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const siteStore = useSiteStore()
const memberStore = useMemberStore()

const keyword = ref(route.query.keyword || '')
const scrolled = ref(false)
const siteName = ref('bcBlog')
const siteIcp = ref('')
const siteLogo = ref('')
const showParticles = ref(true)
const uptimeText = ref('--')
const nowText = ref('')
let footerTimer = 0
let baseUptime = 0
let uptimeFetchedAt = 0
let particleTimer = 0

watch(
  () => route.query.keyword,
  (val) => {
    keyword.value = val || ''
  }
)

// 统计前台页面访问量（SPA 路由切换也会计数）
watch(
  () => route.path,
  () => {
    reportVisit().catch(() => {})
  }
)

// 主题仅在门户页面生效，离开后恢复默认
watch(
  () => themeStore.isDark,
  () => themeStore.apply()
)

function onSearch() {
  const kw = keyword.value.trim()
  if (kw) {
    router.push({ path: '/portal', query: { keyword: kw } })
  } else {
    router.push({ path: '/portal' })
  }
}

function onScroll() {
  scrolled.value = window.scrollY > 10
}

function toggleParticles() {
  clearTimeout(particleTimer)
  showParticles.value = !showParticles.value
}

async function onMemberCommand(cmd) {
  if (cmd === 'center') {
    router.push('/portal/user')
  } else if (cmd === 'logout') {
    await memberStore.logout()
    ElMessage.success('已退出登录')
    router.push('/portal')
  }
}

/** 把秒数格式化为“X天X小时X分X秒” */
function formatUptime(seconds) {
  const s = Math.max(0, Math.floor(seconds || 0))
  const day = Math.floor(s / 86400)
  const hour = Math.floor((s % 86400) / 3600)
  const minute = Math.floor((s % 3600) / 60)
  const second = s % 60
  if (day > 0) return `${day} 天 ${hour} 小时 ${minute} 分`
  if (hour > 0) return `${hour} 小时 ${minute} 分 ${second} 秒`
  return `${minute} 分 ${second} 秒`
}

/** 每秒刷新底部运行时长和北京时间 */
function updateFooterStatus() {
  const now = new Date()
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).formatToParts(now)
  const get = (type) => parts.find((p) => p.type === type)?.value || ''
  nowText.value = `${get('year')}-${get('month')}-${get('day')} ${get('hour')}:${get('minute')}:${get('second')}`
  const elapsed = (Date.now() - uptimeFetchedAt) / 1000
  uptimeText.value = formatUptime(baseUptime + elapsed)
}

/** 公告显示/关闭时同步顶部偏移，避免遮挡正文。 */
onMounted(async () => {
  themeStore.apply()
  reportVisit().catch(() => {})
  memberStore.fetchInfo().catch(() => {})
  const config = await siteStore.load()
  siteName.value = config.siteName || 'bcBlog'
  siteIcp.value = config.siteIcp || ''
  siteLogo.value = config.siteLogo || ''
  applySiteMeta(config)
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
  try {
    const info = await getSystemInfo()
    baseUptime = info.uptimeSeconds || 0
    uptimeFetchedAt = Date.now()
  } catch (e) {
    baseUptime = 0
    uptimeFetchedAt = Date.now()
  }
  updateFooterStatus()
  footerTimer = window.setInterval(updateFooterStatus, 1000)
  // 默认页面加载 5 秒后樱花特效慢慢淡出
  particleTimer = window.setTimeout(() => {
    showParticles.value = false
  }, 5000)
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  clearTimeout(particleTimer)
  clearInterval(footerTimer)
  document.documentElement.classList.remove('dark')
  document.documentElement.removeAttribute('data-theme')
})
</script>

<style scoped>
.portal {
  position: relative;
  min-height: 100vh;
  z-index: 1;
}
.portal-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
  height: var(--header-height);
  transition: background 0.35s ease, box-shadow 0.35s ease, border-color 0.35s ease;
  border-bottom: 1px solid transparent;
}
.portal-header.scrolled {
  background: var(--nav-bg);
  border-bottom-color: var(--border);
  box-shadow: var(--shadow);
  backdrop-filter: blur(18px) saturate(1.4);
  -webkit-backdrop-filter: blur(18px) saturate(1.4);
}
.header-inner {
  max-width: 1320px;
  margin: 0 auto;
  padding: 0 20px;
  height: 100%;
  display: flex;
  align-items: center;
  gap: 18px;
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  font-size: 22px;
  font-weight: 800;
  color: var(--text-strong);
  white-space: nowrap;
}
.brand-text {
  background: linear-gradient(90deg, var(--accent), var(--accent-2));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.brand-logo {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  box-shadow: var(--shadow);
}
.portal-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border-radius: 999px;
  background: var(--glass-bg);
  border: 1px solid var(--border);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  box-shadow: var(--shadow);
  white-space: nowrap;
}
.portal-nav .nav-pill {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 999px;
  overflow: hidden;
  color: var(--text-muted);
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.5px;
  transition: color 0.3s ease, background 0.3s ease, box-shadow 0.3s ease, transform 0.3s ease;
}
.portal-nav .nav-pill svg {
  flex-shrink: 0;
  transition: transform 0.3s ease;
}
.portal-nav .nav-pill::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, transparent 0%, rgba(255, 255, 255, 0.38) 50%, transparent 100%);
  transform: translateX(-130%);
  transition: transform 0.6s ease;
  pointer-events: none;
}
.portal-nav .nav-pill:hover {
  color: var(--text-strong);
  background: var(--accent-soft);
  transform: translateY(-1px);
}
.portal-nav .nav-pill:hover::before {
  transform: translateX(130%);
}
.portal-nav .nav-pill:hover svg {
  transform: scale(1.12) rotate(-4deg);
}
.portal-nav .nav-pill.router-link-active {
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  box-shadow: 0 6px 18px var(--accent-soft), inset 0 0 0 1px rgba(255, 255, 255, 0.18);
}
.portal-nav .nav-pill.router-link-active svg {
  transform: scale(1.08);
}
.search {
  margin-left: auto;
  display: flex;
  align-items: center;
  width: 260px;
  max-width: 46vw;
  background: var(--glass-bg);
  border: 1px solid var(--border);
  border-radius: 999px;
  overflow: hidden;
  backdrop-filter: blur(12px);
}
.search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text);
  padding: 9px 14px;
  font-size: 14px;
}
.search button {
  border: none;
  background: transparent;
  color: var(--text-muted);
  padding: 0 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
}
.theme-toggle {
  width: 40px;
  height: 40px;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: var(--glass-bg);
  color: var(--text);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s ease;
  backdrop-filter: blur(12px);
}
.theme-toggle:hover {
  transform: rotate(20deg) scale(1.06);
}
.theme-toggle.on {
  color: var(--accent);
  border-color: var(--accent);
  background: var(--accent-soft);
}
.portal-main {
  position: relative;
  z-index: 1;
}
.announcement-link {
  text-decoration: none;
}
.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
.member-info {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.member-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--border);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.member-avatar.placeholder {
  color: #fff;
  font-weight: 700;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.member-name {
  color: var(--text);
  font-size: 13px;
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.user-link {
  color: var(--text-muted);
  text-decoration: none;
  font-size: 13px;
  padding: 6px 10px;
  border-radius: 999px;
  transition: all 0.25s ease;
}
.user-link:hover {
  color: var(--accent);
}
.user-link.primary {
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  box-shadow: var(--shadow);
}
.portal-footer {
  position: relative;
  z-index: 1;
  margin-top: 60px;
  padding: 36px 20px;
  border-top: 1px solid var(--border);
  background: var(--glass-bg);
  backdrop-filter: blur(14px);
}
.footer-inner {
  max-width: 1320px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.footer-brand {
  font-weight: 700;
  color: var(--text-strong);
}
.footer-status {
  display: flex;
  align-items: center;
  gap: 18px;
  color: var(--text-muted);
  font-size: 13px;
  flex-wrap: wrap;
}
.status-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #67c23a;
  box-shadow: 0 0 0 4px rgba(103, 194, 58, 0.15);
}
.status-time {
  font-variant-numeric: tabular-nums;
}
.footer-links {
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--text-muted);
  font-size: 13px;
}
.icp {
  color: var(--text-muted);
}
.footer-link {
  color: var(--text-muted);
  text-decoration: none;
}
.footer-link:hover {
  color: var(--accent);
}
@media (max-width: 640px) {
  .header-inner {
    padding: 0 12px;
    gap: 10px;
  }
  .brand {
    font-size: 19px;
  }
  .search {
    width: 150px;
  }
  .portal-nav {
    display: none;
  }
  .member-name {
    display: none;
  }
}
</style>
