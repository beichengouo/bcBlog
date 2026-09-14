<template>
  <div class="gitalk-wrap">
    <h2 class="gitalk-title">评论</h2>
    <div ref="containerRef" class="gitalk-container"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import Gitalk from 'gitalk'
import 'gitalk/dist/gitalk.css'
import { getGitalkConfig } from '@/api/gitalk'

const props = defineProps({
  title: { type: String, default: '' }
})

const route = useRoute()
const containerRef = ref()
let config = null

/** 拉取一次 Gitalk 配置并缓存 */
async function ensureConfig() {
  if (config) return config
  try {
    config = await getGitalkConfig()
  } catch (e) {
    config = null
  }
  return config
}

async function renderGitalk() {
  const cfg = await ensureConfig()
  await nextTick()
  const el = containerRef.value
  if (!el) return
  el.innerHTML = ''

  if (!cfg || !cfg.clientId || !cfg.repo || !cfg.owner) {
    el.innerHTML = '<p class="gitalk-tip">Gitalk 评论尚未配置，请联系管理员。</p>'
    return
  }

  const gitalk = new Gitalk({
    clientID: cfg.clientId,
    clientSecret: cfg.clientSecret || '',
    repo: cfg.repo,
    owner: cfg.owner,
    admin: cfg.admin && cfg.admin.length ? cfg.admin : [cfg.owner],
    id: route.path.substring(0, 49),
    title: props.title || document.title,
    proxy: '/api/portal/gitalk/oauth',
    distractionFreeMode: false,
    language: 'zh-CN'
  })
  gitalk.render(el)
}

onMounted(renderGitalk)
watch(() => route.path, () => renderGitalk())
onUnmounted(() => {
  if (containerRef.value) {
    containerRef.value.innerHTML = ''
  }
})
</script>

<style scoped>
.gitalk-wrap {
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
.gitalk-title {
  font-size: 18px;
  margin: 0 0 16px;
  color: var(--text-strong);
}
.gitalk-container {
  min-height: 80px;
}
.gitalk-tip {
  color: var(--text-muted);
  font-size: 14px;
}
</style>

<!-- Gitalk 自带样式是全局的，这里做毛玻璃主题适配 -->
<style>
.gitalk-container .gt-container .gt-header-textarea {
  background: var(--glass-bg) !important;
  backdrop-filter: blur(12px) !important;
  border: 1px solid var(--border) !important;
  border-radius: 14px !important;
  color: var(--text) !important;
}
.gitalk-container .gt-container .gt-header-textarea:focus {
  border-color: var(--accent) !important;
  box-shadow: 0 0 15px var(--accent-soft) !important;
}
.gitalk-container .gt-container .gt-header-preview {
  background: var(--glass-bg) !important;
  border-radius: 14px !important;
}
.gitalk-container .gt-container .gt-btn {
  background: linear-gradient(135deg, var(--accent), var(--accent-2)) !important;
  border: none !important;
  border-radius: 10px !important;
  color: #fff !important;
}
.gitalk-container .gt-container .gt-comment-content {
  background: var(--glass-bg) !important;
  backdrop-filter: blur(8px) !important;
  border: 1px solid var(--border) !important;
  border-radius: 14px !important;
}
.gitalk-container .gt-container .gt-avatar {
  border-radius: 50% !important;
  overflow: hidden;
}
.gitalk-container .gt-container .gt-comment-body,
.gitalk-container .gt-container,
.gitalk-container .gt-container .gt-meta,
.gitalk-container .gt-container .gt-counts {
  color: var(--text) !important;
}
.gitalk-container .gt-container a {
  color: var(--accent) !important;
}
</style>
