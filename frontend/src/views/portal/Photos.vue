<template>
  <div class="photos-page">
    <header class="page-head">
      <h1 class="page-title">流光忆庭</h1>
      <p class="page-sub">把走过的路、看过的风景，安静地留在这里</p>
    </header>

    <div v-if="loading" class="loading">加载中...</div>
    <el-empty v-else-if="!list.length" description="还没有照片" />
    <div v-else class="photo-wall">
      <figure
        v-for="(p, i) in list"
        :key="p.id"
        class="photo-item"
        @click="open(i)"
      >
        <img :src="p.url" :alt="p.title || ''" loading="lazy" />
        <figcaption v-if="p.title || p.description">
          <span v-if="p.title" class="photo-title">{{ p.title }}</span>
          <span v-if="p.description" class="photo-desc">{{ p.description }}</span>
        </figcaption>
      </figure>
    </div>

    <teleport to="body">
      <transition name="lightbox">
        <div v-if="lightboxIndex >= 0" class="lightbox" @click="close">
          <button class="lb-btn prev" @click.stop="step(-1)" aria-label="上一张">
            <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6" /></svg>
          </button>
          <img :src="current.url" :alt="current.title || ''" @click.stop />
          <button class="lb-btn next" @click.stop="step(1)" aria-label="下一张">
            <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18l6-6-6-6" /></svg>
          </button>
          <div class="lb-caption" v-if="current.title || current.description" @click.stop>
            <strong v-if="current.title">{{ current.title }}</strong>
            <span v-if="current.description">{{ current.description }}</span>
          </div>
          <button class="lb-btn close" @click.stop="close" aria-label="关闭">×</button>
        </div>
      </transition>
    </teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { portalPhotoList } from '@/api/photo'

const list = ref([])
const loading = ref(false)
const lightboxIndex = ref(-1)

const current = computed(() => list.value[lightboxIndex.value] || {})

async function load() {
  loading.value = true
  try {
    list.value = await portalPhotoList()
  } finally {
    loading.value = false
  }
}

function open(i) {
  lightboxIndex.value = i
}

function close() {
  lightboxIndex.value = -1
}

function step(delta) {
  if (!list.value.length) return
  lightboxIndex.value = (lightboxIndex.value + delta + list.value.length) % list.value.length
}

function onKeydown(e) {
  if (lightboxIndex.value < 0) return
  if (e.key === 'Escape') close()
  else if (e.key === 'ArrowLeft') step(-1)
  else if (e.key === 'ArrowRight') step(1)
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.photos-page {
  max-width: 1180px;
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
.photo-wall {
  column-count: 3;
  column-gap: 18px;
}
.photo-item {
  break-inside: avoid;
  margin: 0 0 18px;
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  cursor: zoom-in;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
.photo-item:hover {
  transform: translateY(-5px);
  box-shadow: var(--shadow-hover);
}
.photo-item img {
  width: 100%;
  display: block;
}
.photo-item figcaption {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.photo-title {
  font-weight: 600;
  color: var(--text-strong);
  font-size: 14px;
}
.photo-desc {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}
.lightbox {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.86);
  backdrop-filter: blur(8px);
}
.lightbox img {
  max-width: 92vw;
  max-height: 84vh;
  border-radius: 12px;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.5);
}
.lb-btn {
  position: absolute;
  width: 46px;
  height: 46px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease;
}
.lb-btn:hover {
  background: rgba(255, 255, 255, 0.28);
}
.lb-btn.prev { left: 20px; top: 50%; transform: translateY(-50%); }
.lb-btn.next { right: 20px; top: 50%; transform: translateY(-50%); }
.lb-btn.close { top: 20px; right: 20px; font-size: 26px; }
.lb-caption {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  max-width: 80vw;
  padding: 10px 16px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: center;
}
.lb-caption span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
}
.lightbox-enter-active,
.lightbox-leave-active {
  transition: opacity 0.22s ease;
}
.lightbox-enter-from,
.lightbox-leave-to {
  opacity: 0;
}
@media (max-width: 1024px) {
  .photo-wall { column-count: 2; }
}
@media (max-width: 640px) {
  .photo-wall { column-count: 1; }
}
</style>
