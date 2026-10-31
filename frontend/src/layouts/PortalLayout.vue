<template>
  <div class="portal">
    <!-- 背景壁纸（图片或视频） -->
    <BackgroundLayer scope="portal" />
    <!-- 背景粒子（白天樱花 / 夜晚星空） -->
    <ParticleCanvas :active="showParticles" />
    <ClickRipple />
    <ReadingProgress />

    <button v-if="mobileMenuOpen || mobileSearchOpen" class="nav-backdrop" type="button" tabindex="-1" aria-label="关闭导航面板" @click="closeHeaderPanels" />
    <header class="portal-header" :class="{ scrolled, 'panels-open': mobileMenuOpen || mobileSearchOpen }" @keydown.esc.stop.prevent="dismissHeaderPanels">
      <div class="header-inner">
        <router-link to="/portal" class="brand" :aria-label="siteName + '首页'" @click="closeHeaderPanels">
          <img v-if="siteLogo && !logoFailed" class="brand-logo" :src="siteLogo" alt="" @error="logoFailed = true" />
          <span v-else class="brand-mark" aria-hidden="true">{{ siteName.slice(0, 1) }}</span>
          <span class="brand-copy"><span class="brand-text">{{ siteName }}</span><span class="brand-caption">记录 · 分享 · 探索</span></span>
        </router-link>

        <nav class="portal-nav" aria-label="主导航">
          <router-link v-for="item in navItems" :key="item.path" :to="item.path" class="nav-pill" :class="{ 'is-active': isNavActive(item.path) }" :aria-current="isNavActive(item.path) ? 'page' : undefined">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="item.icon" /></svg>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>

        <div class="header-actions">
          <button ref="searchToggleRef" class="theme-toggle search-toggle" type="button" :class="{ on: mobileSearchOpen }" :aria-label="mobileSearchOpen ? '收起搜索' : '搜索文章'" :aria-expanded="mobileSearchOpen" aria-controls="header-search" @click="toggleHeaderSearch">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4 4" /></svg>
          </button>
          <form id="header-search" class="search" :class="{ 'search-open': mobileSearchOpen }" role="search" @submit.prevent="onSearch">
            <input ref="searchInputRef" v-model="keyword" type="search" placeholder="发现一篇好文章…" aria-label="搜索文章" />
            <button type="submit" aria-label="提交搜索"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4 4" /></svg></button>
          </form>

          <div class="appearance-tools" role="group" aria-label="外观设置">
            <button type="button" class="theme-toggle" @click="themeStore.toggle()" :title="themeStore.isDark ? '切换到白天' : '切换到夜晚'" :aria-label="themeStore.isDark ? '切换到白天' : '切换到夜晚'">
              <svg v-if="themeStore.isDark" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.4 1.4m11.2 11.2L19 19M5 19l1.4-1.4M17.6 6.4 19 5" /></svg>
              <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" /></svg>
            </button>
            <button type="button" class="theme-toggle ambient-toggle" :class="{ on: showParticles }" :aria-pressed="showParticles" @click="toggleParticles" :aria-label="showParticles ? '关闭粒子特效' : '开启粒子特效'" :title="showParticles ? '关闭粒子特效' : '开启粒子特效'">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" aria-hidden="true"><path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z" /></svg>
            </button>
            <router-link class="theme-toggle announcement-link" to="/portal/announcements" title="站点公告" aria-label="站点公告">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
            </router-link>
          </div>

          <div class="user-entry">
            <el-dropdown v-if="memberStore.isLogin" trigger="click" @command="onMemberCommand">
              <button type="button" class="member-info" aria-label="打开用户菜单">
                <img v-if="memberStore.userInfo?.avatar" class="member-avatar" :src="memberStore.userInfo.avatar" alt="" />
                <span v-else class="member-avatar placeholder">{{ (memberStore.userInfo?.nickname || memberStore.userInfo?.username || 'U').slice(0, 1) }}</span>
                <span class="member-name">{{ memberStore.userInfo?.nickname || memberStore.userInfo?.username }}</span>
              </button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="center">用户中心</el-dropdown-item><el-dropdown-item command="logout" divided>退出登录</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
            <template v-else><router-link class="user-link" to="/portal/login">登录</router-link><router-link class="user-link primary" to="/portal/login?tab=register">加入小站 <span aria-hidden="true">↗</span></router-link></template>
          </div>
          <button ref="menuToggleRef" class="menu-toggle" type="button" :aria-expanded="mobileMenuOpen" aria-controls="mobile-site-menu" :aria-label="mobileMenuOpen ? '收起栏目菜单' : '打开栏目菜单'" @click="toggleHeaderMenu">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true"><path v-if="mobileMenuOpen" d="m6 6 12 12M6 18 18 6" /><path v-else d="M4 7h16M4 12h16M4 17h10" /></svg>
          </button>
        </div>
      </div>

      <nav v-if="mobileMenuOpen" id="mobile-site-menu" class="mobile-menu" aria-label="移动端栏目">
        <div class="mobile-menu-heading"><span>找一处，随心逛逛</span><span>EXPLORE</span></div>
        <router-link v-for="item in navItems" :key="item.path" :to="item.path" class="mobile-menu-link" :class="{ 'is-active': isNavActive(item.path) }" :aria-current="isNavActive(item.path) ? 'page' : undefined" @click="closeHeaderPanels">
          <span class="mobile-menu-icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path :d="item.icon" /></svg></span>
          <span class="mobile-menu-copy"><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span><span class="mobile-menu-arrow" aria-hidden="true">↗</span>
        </router-link>
        <router-link to="/portal/announcements" class="mobile-announcement" @click="closeHeaderPanels">站点公告 <span aria-hidden="true">→</span></router-link>
      </nav>
    </header>

    <main id="main-content" class="portal-main">
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
        <a
          v-if="siteIcp"
          class="icp"
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
          :title="siteIcp + '（工信部备案查询）'"
        >{{ siteIcp }}</a>
          <router-link class="footer-link" to="/portal/photos">流光忆庭</router-link>
          <router-link class="footer-link" to="/portal/resources">智库</router-link>
          <router-link class="footer-link" to="/portal/iris">IRIS</router-link>
          <router-link class="footer-link" to="/portal/games/pixelrogue">游戏作品</router-link>
          <router-link class="footer-link" to="/portal/terms">用户协议</router-link>
          <router-link class="footer-link" to="/portal/privacy">隐私政策</router-link>
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
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
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
/** 手机端搜索框是否展开（默认收起成一个放大镜图标） */
const mobileSearchOpen = ref(false)
const mobileMenuOpen = ref(false)
const searchInputRef = ref(null)
const searchToggleRef = ref(null)
const menuToggleRef = ref(null)
const logoFailed = ref(false)
const navItems = [
  { path: '/portal', label: '首页', description: '从最新的记录开始', icon: 'm3 10 9-7 9 7v10H3z M9 20v-7h6v7' },
  { path: '/portal/games/pixelrogue', label: '游戏', description: '轮舞于星辉尽头 · 独立游戏', icon: 'M7 7h10l4 10-3 3-4-4h-4l-4 4-3-3z M7 10v5 M4.5 12.5h5 M16 11h.01 M18 14h.01' },
  { path: '/portal/iris', label: 'IRIS', description: '和伊莉丝交换一点灵感', icon: 'm12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z' },
  { path: '/portal/photos', label: '流光忆庭', description: '收藏光影与日常', icon: 'M4 4h16v16H4z M8 9h.01 M4 17l5-5 4 4 3-3 4 4' },
  { path: '/portal/resources', label: '智库', description: '好工具与好想法，都在这里', icon: 'M4 5c3-2 6-2 8 0 2-2 5-2 8 0v15c-3-2-6-2-8 0-2-2-5-2-8 0z M12 5v15' },
  { path: '/portal/sandbox', label: '沙盒', description: '走进旅人们的故事', icon: 'm3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3z M9 3v15 M15 6v15' }
]
function isNavActive(path) {
  if (path === '/portal') return /^\/portal\/?$/.test(route.path)
  return route.path === path || route.path.startsWith(path + '/')
}
function closeHeaderPanels() {
  mobileMenuOpen.value = false
  mobileSearchOpen.value = false
}
function toggleHeaderMenu() {
  mobileSearchOpen.value = false
  mobileMenuOpen.value = !mobileMenuOpen.value
}
function toggleHeaderSearch() {
  mobileMenuOpen.value = false
  mobileSearchOpen.value = !mobileSearchOpen.value
}
async function dismissHeaderPanels() {
  const trigger = mobileMenuOpen.value ? menuToggleRef.value : searchToggleRef.value
  closeHeaderPanels()
  await nextTick()
  trigger?.focus()
}
watch(() => route.fullPath, closeHeaderPanels)
watch(mobileSearchOpen, async (open) => {
  if (!open) return
  await nextTick()
  if (mobileSearchOpen.value) searchInputRef.value?.focus()
})
let headerObserver = null

/**
 * 把头部真实高度写进 --header-height。
 *
 * 为什么必须动态算：以前这个变量写死 64px，手机端头部变成两行后，
 * 靠它定位的天气卡片（top: calc(var(--header-height) + 22px)）就压到了头部上，
 * 各页面的顶部留白也会偏小。这里统一量一次，全站跟着走。
 */
function syncHeaderHeight() {
  if (window.innerWidth > 1240) mobileSearchOpen.value = false
  if (window.innerWidth > 1100) mobileMenuOpen.value = false
  const el = document.querySelector('.portal-header')
  if (el) {
    document.documentElement.style.setProperty(
      '--header-height',
      Math.round(el.getBoundingClientRect().height) + 'px'
    )
  }
}
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

watch(siteLogo, () => { logoFailed.value = false })

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
  closeHeaderPanels()
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
  // 头部高度量一次（手机端是两行，写死的 64px 会让天气卡片压住头部），并跟随窗口变化重算
  syncHeaderHeight()
  window.addEventListener('resize', syncHeaderHeight, { passive: true })
  if (window.ResizeObserver) {
    headerObserver = new ResizeObserver(syncHeaderHeight)
    const headerEl = document.querySelector('.portal-header')
    if (headerEl) {
      headerObserver.observe(headerEl)
    }
  }
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
  window.removeEventListener('resize', syncHeaderHeight)
  if (headerObserver) { headerObserver.disconnect(); headerObserver = null }
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
.portal-main {
  position: relative;
  z-index: 1;
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
  flex-wrap: wrap;
  row-gap: 10px;
}
.icp {
  color: var(--text-muted);
  text-decoration: none;
  transition: color 0.2s ease;
}
.icp:hover {
  color: var(--accent);
  text-decoration: underline;
}
.footer-link {
  color: var(--text-muted);
  text-decoration: none;
}
.footer-link:hover {
  color: var(--accent);
}
</style>

<style scoped src="@/styles/portal-navigation.css"></style>
