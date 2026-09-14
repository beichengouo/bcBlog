<template>
  <div class="dashboard">
    <!-- 核心数据 -->
    <div class="stat-grid">
      <el-card v-for="item in cards" :key="item.label" class="stat-card">
        <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
        <div class="stat-label">{{ item.label }}</div>
      </el-card>
    </div>

    <!-- 访问量 -->
    <el-card class="section">
      <template #header>
        <div class="section-head">
          <span>访问量统计</span>
          <span class="section-sub">前台页面访问</span>
        </div>
      </template>
      <div class="visit-top">
        <div class="visit-num">
          <span class="visit-label">总访问量 PV</span>
          <span class="visit-value">{{ stats?.visitStats?.totalPv ?? 0 }}</span>
        </div>
        <div class="visit-num">
          <span class="visit-label">今日访问量</span>
          <span class="visit-value accent">{{ stats?.visitStats?.todayPv ?? 0 }}</span>
        </div>
      </div>
      <div class="chart">
        <div v-for="d in stats?.visitStats?.last7Days || []" :key="d.date" class="chart-col">
          <div class="chart-bar-wrap">
            <div class="chart-bar" :style="{ height: barHeight(d.pv) }" :title="`${d.date}：${d.pv}`"></div>
          </div>
          <span class="chart-date">{{ d.date.slice(5) }}</span>
          <span class="chart-pv">{{ d.pv }}</span>
        </div>
      </div>
    </el-card>

    <!-- 系统性能监控 -->
    <el-card class="section" v-loading="!monitor">
      <template #header>
        <div class="section-head">
          <span>系统性能监控</span>
          <span class="section-sub">{{ monitor?.serverTime || '—' }}</span>
        </div>
      </template>
      <div class="progress-grid">
        <div class="progress-item">
          <div class="progress-head"><span>CPU 使用率</span><span>{{ monitor?.cpuUsage ?? 0 }}%</span></div>
          <el-progress :percentage="toPercent(monitor?.cpuUsage)" :stroke-width="10" :show-text="false" :color="barColor(monitor?.cpuUsage)" />
        </div>
        <div class="progress-item">
          <div class="progress-head"><span>物理内存</span><span>{{ monitor?.memoryUsage ?? 0 }}%</span></div>
          <el-progress :percentage="toPercent(monitor?.memoryUsage)" :stroke-width="10" :show-text="false" :color="barColor(monitor?.memoryUsage)" />
          <div class="progress-sub">{{ formatBytes(monitor?.memoryUsed) }} / {{ formatBytes(monitor?.memoryTotal) }}</div>
        </div>
        <div class="progress-item">
          <div class="progress-head"><span>JVM 堆内存</span><span>{{ monitor?.jvmHeapUsage ?? 0 }}%</span></div>
          <el-progress :percentage="toPercent(monitor?.jvmHeapUsage)" :stroke-width="10" :show-text="false" :color="barColor(monitor?.jvmHeapUsage)" />
          <div class="progress-sub">{{ formatBytes(monitor?.jvmHeapUsed) }} / {{ formatBytes(monitor?.jvmHeapMax) }}</div>
        </div>
        <div class="progress-item">
          <div class="progress-head"><span>磁盘使用率</span><span>{{ monitor?.diskUsage ?? 0 }}%</span></div>
          <el-progress :percentage="toPercent(monitor?.diskUsage)" :stroke-width="10" :show-text="false" :color="barColor(monitor?.diskUsage)" />
          <div class="progress-sub">{{ formatBytes(monitor?.diskUsed) }} / {{ formatBytes(monitor?.diskTotal) }}</div>
        </div>
      </div>
      <div class="mini-stats">
        <div class="mini-item"><span class="mini-label">JVM 线程</span><span class="mini-value">{{ monitor?.threadCount ?? 0 }}</span></div>
        <div class="mini-item"><span class="mini-label">CPU 核心</span><span class="mini-value">{{ monitor?.availableProcessors ?? 0 }}</span></div>
        <div class="mini-item"><span class="mini-label">系统负载</span><span class="mini-value">{{ monitor?.systemLoadAverage ?? 0 }}</span></div>
        <div class="mini-item"><span class="mini-label">运行时长</span><span class="mini-value">{{ formatUptime(monitor?.uptimeSeconds) }}</span></div>
      </div>
    </el-card>

    <!-- 网站运行数据 -->
    <el-card class="section">
      <template #header>
        <div class="section-head">
          <span>网站运行数据</span>
          <span class="section-sub">后端启动于 {{ stats?.startTime || '—' }}</span>
        </div>
      </template>
      <div class="run-grid">
        <div class="run-item">
          <span class="run-label">系统已稳定运行</span>
          <span class="run-value">{{ uptimeText }}</span>
        </div>
        <div class="run-item">
          <span class="run-label">文章总浏览量</span>
          <span class="run-value">{{ stats?.viewCount ?? 0 }}</span>
        </div>
        <div class="run-item">
          <span class="run-label">照片墙照片</span>
          <span class="run-value">{{ stats?.photoCount ?? 0 }}</span>
        </div>
        <div class="run-item">
          <span class="run-label">智库资源</span>
          <span class="run-value">{{ stats?.resourceCount ?? 0 }}</span>
        </div>
        <div class="run-item">
          <span class="run-label">管理员账号</span>
          <span class="run-value">{{ stats?.adminCount ?? 0 }}</span>
        </div>
      </div>
    </el-card>

    <!-- 文章数据 -->
    <el-row :gutter="16" class="section">
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>热门文章 TOP 5</template>
          <ul class="article-rank">
            <li v-for="(a, i) in stats?.topArticles || []" :key="a.id" @click="goArticle(a.id)">
              <span class="rank" :class="{ top: i < 3 }">{{ i + 1 }}</span>
              <span class="rank-title">{{ a.title }}</span>
              <span class="rank-meta">{{ a.viewCount || 0 }} 次</span>
            </li>
            <li v-if="!(stats?.topArticles || []).length" class="empty">暂无数据</li>
          </ul>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>最新文章</template>
          <ul class="article-rank">
            <li v-for="a in stats?.recentArticles || []" :key="a.id" @click="goArticle(a.id)">
              <span class="rank-title">{{ a.title }}</span>
              <span class="rank-meta">{{ shortDate(a.createTime) }}</span>
            </li>
            <li v-if="!(stats?.recentArticles || []).length" class="empty">暂无数据</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近评论 -->
    <el-card class="section">
      <template #header>
        <div class="section-head">
          <span>最近评论（Gitalk · GitHub）</span>
          <span class="section-sub">最多显示 10 条</span>
        </div>
      </template>
      <div v-if="(stats?.recentComments || []).length" class="comment-list">
        <div v-for="c in stats.recentComments" :key="c.id" class="comment-item" @click="onCommentClick(c)">
          <img class="comment-avatar" :src="c.avatar" alt="" />
          <div class="comment-body">
            <div class="comment-meta">
              <span class="comment-author">{{ c.author }}</span>
              <span class="comment-time">{{ formatTime(c.createdAt) }}</span>
            </div>
            <p class="comment-text">{{ c.body }}</p>
            <div v-if="c.pageTitle" class="comment-page">评论于《{{ c.pageTitle }}》</div>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无 Gitalk 评论" :image-size="70" />
    </el-card>

    <!-- 系统信息与依赖 -->
    <el-card class="section" v-loading="!monitor">
      <template #header>
        <div class="section-head">
          <span>系统信息与依赖</span>
          <span class="section-sub">运行环境 / 版本信息</span>
        </div>
      </template>
      <div class="sys-info-grid">
        <div class="sys-info-item"><span class="sys-label">操作系统</span><span class="sys-value">{{ monitor?.osName }}</span></div>
        <div class="sys-info-item"><span class="sys-label">系统架构</span><span class="sys-value">{{ monitor?.osArch }}</span></div>
        <div class="sys-info-item"><span class="sys-label">Java</span><span class="sys-value">{{ monitor?.javaVersion }}（{{ monitor?.javaVendor }}）</span></div>
        <div class="sys-info-item"><span class="sys-label">JVM</span><span class="sys-value">{{ monitor?.jvmName }}</span></div>
        <div class="sys-info-item"><span class="sys-label">Spring Boot</span><span class="sys-value">{{ monitor?.springBootVersion }}</span></div>
        <div class="sys-info-item"><span class="sys-label">Tomcat</span><span class="sys-value">{{ monitor?.tomcatVersion }}</span></div>
        <div class="sys-info-item"><span class="sys-label">数据库</span><span class="sys-value">{{ monitor?.databaseVersion }}</span></div>
        <div class="sys-info-item"><span class="sys-label">时区 / 时间</span><span class="sys-value">{{ monitor?.serverTimezone }} · {{ monitor?.serverTime }}</span></div>
      </div>

      <el-divider content-position="left">依赖版本</el-divider>
      <div class="dep-list">
        <div v-for="d in monitor?.dependencies || []" :key="d.type + d.name" class="dep-item">
          <span class="dep-type">{{ d.type }}</span>
          <span class="dep-name">{{ d.name }}</span>
          <span class="dep-version">{{ d.version || '未知' }}</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStats } from '@/api/dashboard'
import { getSystemMonitor } from '@/api/system'

const router = useRouter()
const stats = ref(null)
const monitor = ref(null)
let monitorTimer = 0

const cards = computed(() => [
  { label: '文章总数', value: stats.value?.articleCount ?? 0, color: '#409eff' },
  { label: '已发布', value: stats.value?.publishedCount ?? 0, color: '#67c23a' },
  { label: '草稿', value: stats.value?.draftCount ?? 0, color: '#e6a23c' },
  { label: '分类数', value: stats.value?.categoryCount ?? 0, color: '#909399' },
  { label: '标签数', value: stats.value?.tagCount ?? 0, color: '#909399' },
  { label: '总浏览量', value: stats.value?.viewCount ?? 0, color: '#f56c6c' },
  { label: '今日访问', value: stats.value?.visitStats?.todayPv ?? 0, color: '#a06bd8' },
  { label: '总访问量', value: stats.value?.visitStats?.totalPv ?? 0, color: '#a06bd8' },
  { label: '照片墙', value: stats.value?.photoCount ?? 0, color: '#a06bd8' },
  { label: '智库资源', value: stats.value?.resourceCount ?? 0, color: '#a06bd8' }
])

const uptimeText = computed(() => formatUptime(stats.value?.uptimeSeconds || 0))
const maxPv = computed(() => {
  const arr = stats.value?.visitStats?.last7Days || []
  return Math.max(1, ...arr.map((d) => d.pv || 0))
})

function barHeight(pv) {
  const h = Math.round(((pv || 0) / maxPv.value) * 100)
  return Math.max(h, pv > 0 ? 6 : 2) + '%'
}

function toPercent(v) {
  const n = Number(v || 0)
  return Math.max(0, Math.min(100, Math.round(n)))
}

function barColor(v) {
  const n = Number(v || 0)
  if (n >= 85) return '#f56c6c'
  if (n >= 65) return '#e6a23c'
  return '#67c23a'
}

function formatBytes(bytes) {
  const b = Number(bytes || 0)
  if (!b) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  let v = b
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

function formatUptime(seconds) {
  const s = Math.max(0, Math.floor(seconds || 0))
  const day = Math.floor(s / 86400)
  const hour = Math.floor((s % 86400) / 3600)
  const minute = Math.floor((s % 3600) / 60)
  if (day > 0) return `${day} 天 ${hour} 小时 ${minute} 分`
  if (hour > 0) return `${hour} 小时 ${minute} 分`
  return `${minute} 分`
}

function shortDate(dt) {
  return dt ? String(dt).slice(0, 10) : ''
}

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function goArticle(id) {
  router.push(`/admin/articles/edit/${id}`)
}

function onCommentClick(c) {
  if (c.pagePath && c.pagePath.startsWith('/')) {
    window.open(c.pagePath, '_blank')
  } else if (c.htmlUrl) {
    window.open(c.htmlUrl, '_blank')
  }
}

async function loadMonitor() {
  try {
    monitor.value = await getSystemMonitor()
  } catch (e) {
    // 监控数据获取失败时保留上一次数据
  }
}

onMounted(async () => {
  stats.value = await getStats()
  loadMonitor()
  monitorTimer = window.setInterval(loadMonitor, 5000)
})

onUnmounted(() => {
  clearInterval(monitorTimer)
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 14px;
}
.stat-card {
  text-align: center;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-label {
  margin-top: 8px;
  color: #909399;
  font-size: 13px;
}
.section {
  margin: 0;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.section-sub {
  color: #909399;
  font-size: 12px;
  font-weight: 400;
}

/* 访问量 */
.visit-top {
  display: flex;
  gap: 32px;
  margin-bottom: 18px;
}
.visit-num {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.visit-label {
  color: #909399;
  font-size: 13px;
}
.visit-value {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
}
.visit-value.accent {
  color: #a06bd8;
}
.chart {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  height: 140px;
  padding-top: 10px;
}
.chart-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  height: 100%;
}
.chart-bar-wrap {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.chart-bar {
  width: 60%;
  max-width: 34px;
  min-height: 3px;
  border-radius: 6px 6px 0 0;
  background: linear-gradient(180deg, #a06bd8, #ff6f9f);
  transition: height 0.5s ease;
}
.chart-date {
  color: #909399;
  font-size: 11px;
}
.chart-pv {
  color: #606266;
  font-size: 11px;
}

/* 性能监控 */
.progress-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 18px 24px;
}
.progress-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #606266;
}
.progress-sub {
  color: #909399;
  font-size: 12px;
}
.mini-stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  margin-top: 18px;
}
.mini-item {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.mini-label {
  color: #909399;
  font-size: 13px;
}
.mini-value {
  font-weight: 700;
  color: #303133;
}

/* 运行数据 */
.run-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
}
.run-item {
  padding: 14px;
  border-radius: 10px;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.run-label {
  color: #909399;
  font-size: 13px;
}
.run-value {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
}

/* 文章排行 */
.article-rank {
  list-style: none;
  margin: 0;
  padding: 0;
}
.article-rank li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px dashed #e4e7ed;
  cursor: pointer;
}
.article-rank li:last-child {
  border-bottom: none;
}
.rank {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  background: #e4e7ed;
  color: #909399;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.rank.top {
  background: linear-gradient(135deg, #ff6f9f, #a06bd8);
  color: #fff;
}
.rank-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-meta {
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}
.empty {
  color: #909399;
  justify-content: center;
  cursor: default;
}

/* 最近评论 */
.comment-list {
  display: flex;
  flex-direction: column;
}
.comment-item {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px dashed #e4e7ed;
  cursor: pointer;
}
.comment-item:last-child {
  border-bottom: none;
}
.comment-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  flex-shrink: 0;
}
.comment-body {
  min-width: 0;
}
.comment-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}
.comment-author {
  font-weight: 600;
  color: #303133;
}
.comment-time {
  color: #909399;
  font-size: 12px;
}
.comment-text {
  margin: 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
.comment-page {
  margin-top: 4px;
  color: #409eff;
  font-size: 12px;
}

/* 系统信息 */
.sys-info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px 24px;
}
.sys-info-item {
  display: flex;
  gap: 12px;
  align-items: baseline;
  font-size: 13px;
}
.sys-label {
  width: 90px;
  color: #909399;
  flex-shrink: 0;
}
.sys-value {
  color: #303133;
  word-break: break-all;
}
.dep-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 10px;
}
.dep-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f5f7fa;
  font-size: 13px;
}
.dep-type {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--accent-soft, #e8f0ff);
  color: #409eff;
  font-size: 11px;
  flex-shrink: 0;
}
.dep-name {
  flex: 1;
  color: #303133;
}
.dep-version {
  color: #909399;
  font-family: Consolas, monospace;
  font-size: 12px;
}
</style>
