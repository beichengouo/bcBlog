<template>
  <div class="hitokoto glass" :class="{ open }">
    <button class="fab" @click="open = !open">
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
        <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
      </svg>
    </button>
    <transition name="pop">
      <div v-if="open" class="panel">
        <div class="head">
          <span class="label">一言</span>
          <button class="refresh" @click="load" :disabled="loading" title="换一句">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6M1 20v-6h6" />
              <path d="M3.5 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.5 15" />
            </svg>
          </button>
          <button class="refresh" @click="copy" title="复制这句话">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="11" height="11" rx="2" />
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
            </svg>
          </button>
        </div>
        <p class="text">{{ loading ? '加载中...' : text }}</p>
        <div v-if="!loading && source" class="source">—— {{ source }}</div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getPortalConfig } from '@/api/config'
import { copyText } from '@/utils/content'

const open = ref(false)
const loading = ref(false)
const text = ref('')
const source = ref('')
const categories = ref('d,i,k')

async function load() {
  loading.value = true
  try {
    const res = await fetch(`https://v1.hitokoto.cn/?${categories.value.split(',').filter(Boolean).map((c) => `c=${c.trim()}`).join('&')}`)
    const data = await res.json()
    text.value = data.hitokoto || '世界那么大，还是遇见你。'
    source.value = data.from_who ? `${data.from} · ${data.from_who}` : data.from || ''
  } catch (e) {
    text.value = '愿你历尽千帆，归来仍是少年。'
    source.value = '备用语'
  } finally {
    loading.value = false
  }
}

async function copy() {
  try {
    await copyText(`${text.value} —— ${source.value}`)
  } catch (e) {
    // 复制失败时忽略
  }
}

onMounted(async () => {
  try {
    const config = await getPortalConfig()
    if (config.hitokotoCategories) {
      categories.value = config.hitokotoCategories
    }
  } catch (e) {
    // 使用默认分类
  }
  load()
})
</script>

<style scoped>
.hitokoto {
  position: fixed;
  left: 18px;
  bottom: 74px;
  z-index: 60;
  border-radius: 22px;
  box-shadow: var(--shadow);
}
.fab {
  width: 46px;
  height: 46px;
  border: none;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow);
}
.panel {
  width: 280px;
  padding: 14px 16px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.label {
  font-size: 13px;
  color: var(--text-muted);
  letter-spacing: 1px;
}
.refresh {
  border: none;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
  padding: 4px;
}
.refresh:disabled {
  opacity: 0.4;
  cursor: default;
}
.text {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text);
}
.source {
  margin-top: 8px;
  text-align: right;
  font-size: 12px;
  color: var(--text-muted);
}
.pop-enter-active,
.pop-leave-active {
  transition: all 0.22s ease;
}
.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.96);
}
@media (max-width: 768px) {
  .hitokoto {
    left: 12px;
    bottom: 68px;
  }
}
</style>
