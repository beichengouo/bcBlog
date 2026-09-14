<template>
  <div class="resources-page">
    <header class="page-head">
      <h1 class="page-title">智库</h1>
      <p class="page-sub">整理收藏的资源链接，需要时可以直接取用</p>
    </header>

    <div v-if="loading" class="loading">加载中...</div>
    <el-empty v-else-if="!list.length" description="智库暂时还是空的" />
    <div v-else class="resource-list">
      <article v-for="r in list" :key="r.id" class="resource-card glass">
        <div class="resource-main">
          <h3 class="resource-title">{{ r.title }}</h3>
          <p v-if="r.description" class="resource-desc">{{ r.description }}</p>
          <div class="resource-meta">
            <span class="link-text" :title="r.url">{{ r.url }}</span>
          </div>
        </div>
        <div class="resource-actions">
          <div v-if="r.password" class="password-box">
            <span class="password-label">提取码</span>
            <code class="password-value">{{ r.password }}</code>
            <button class="copy-btn" @click="copy(r.password)">复制</button>
          </div>
          <a class="go-btn" :href="normalizeUrl(r.url)" target="_blank" rel="noopener noreferrer">
            前往资源
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M7 17 17 7M8 7h9v9" />
            </svg>
          </a>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { portalResourceList } from '@/api/resource'
import { copyText } from '@/utils/content'

const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await portalResourceList()
  } finally {
    loading.value = false
  }
}

function normalizeUrl(url) {
  if (!url) return '#'
  return /^https?:\/\//i.test(url) ? url : `https://${url}`
}

async function copy(text) {
  try {
    await copyText(text)
    ElMessage.success('已复制提取码')
  } catch (e) {
    ElMessage.warning('复制失败，请手动复制')
  }
}

onMounted(load)
</script>

<style scoped>
.resources-page {
  max-width: 980px;
  margin: 0 auto;
  padding: calc(var(--header-height) + 28px) 20px 40px;
}
.page-head {
  text-align: center;
  margin-bottom: 28px;
}
.page-title {
  margin: 0;
  font-size: 32px;
  background: linear-gradient(120deg, var(--accent), var(--accent-2));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.page-sub {
  margin: 10px 0 0;
  color: var(--text-muted);
  font-size: 14px;
}
.loading {
  text-align: center;
  color: var(--text-muted);
  padding: 60px 0;
}
.resource-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.resource-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 22px;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  border: 1px solid var(--border);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
.resource-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-hover);
}
.resource-main {
  min-width: 0;
}
.resource-title {
  margin: 0 0 6px;
  font-size: 18px;
  color: var(--text-strong);
}
.resource-desc {
  margin: 0 0 8px;
  color: var(--text-muted);
  font-size: 14px;
  line-height: 1.7;
}
.resource-meta {
  font-size: 12px;
  color: var(--text-muted);
}
.link-text {
  display: inline-block;
  max-width: 520px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.resource-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  flex-shrink: 0;
}
.password-box {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-muted);
}
.password-value {
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--accent-soft);
  color: var(--accent);
}
.copy-btn {
  border: none;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
  font-size: 12px;
}
.go-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 16px;
  border-radius: 999px;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  text-decoration: none;
  font-size: 13px;
  box-shadow: var(--shadow);
  transition: transform 0.2s ease;
}
.go-btn:hover {
  transform: translateY(-2px);
}
@media (max-width: 640px) {
  .resource-card {
    flex-direction: column;
    align-items: flex-start;
  }
  .resource-actions {
    align-items: flex-start;
  }
  .link-text {
    max-width: 70vw;
  }
}
</style>
