<template>
  <section class="side-card glass announcement-board" aria-labelledby="announcement-heading" :aria-busy="loading">
    <div class="board-heading"><span class="board-icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"><path d="M4 10h4l11-5v14L8 14H4z M8 14l2 6h3l-2-5 M22 9v6" /></svg></span><div><span class="board-kicker">NOTICEBOARD</span><h2 id="announcement-heading">小站公告</h2></div><span v-if="!loading && !loadError" class="board-count">{{ list.length }}</span></div>
    <div v-if="loading" class="announcement-loading" role="status"><span></span><span></span><span></span><span class="loading-label">正在读取小站消息…</span></div>
    <div v-else-if="loadError" class="board-empty" role="alert">公告暂时未能加载。<button type="button" class="board-retry" @click="load">再试一次 ↻</button></div>
    <div v-else class="board-list">
      <article v-for="a in list" :key="a.id" class="board-item">
        <div class="board-meta">
          <span class="board-author">{{ a.author || '管理员' }}</span>
          <time class="board-time" :title="a.updateTime || a.createTime">{{ shortTime(a.updateTime || a.createTime) }}</time>
        </div>
        <p :id="'announcement-content-' + a.id" class="board-content" :class="{ expanded: expanded.has(a.id) }">{{ a.content }}</p>
        <button v-if="isLong(a.content)" type="button" class="board-expand" :aria-expanded="expanded.has(a.id)" :aria-controls="'announcement-content-' + a.id" @click="toggleExpanded(a.id)">{{ expanded.has(a.id) ? '收起内容 −' : '展开内容 +' }}</button>
      </article>
      <div v-if="!list.length" class="board-empty">暂时没有新公告，愿你今天也有好心情。</div>
    </div>
    <router-link class="board-more" to="/portal/announcements"><span>查看公告归档</span><span aria-hidden="true">↗</span></router-link>
  </section>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getActiveAnnouncements } from '@/api/announcement'

const list = ref([])
const loading = ref(true)
const loadError = ref(false)
const expanded = ref(new Set())
let requestId = 0

function isLong(content) {
  const text = String(content || '')
  return text.length > 30 || text.split('\n').length > 3
}

function toggleExpanded(id) {
  if (expanded.value.has(id)) expanded.value.delete(id)
  else expanded.value.add(id)
}

function shortTime(val) {
  return val ? String(val).slice(0, 10) : ''
}

async function load() {
  const id = ++requestId
  loading.value = true
  loadError.value = false
  try {
    const data = await getActiveAnnouncements()
    if (id !== requestId) return
    list.value = Array.isArray(data) ? data.slice(0, 5) : []
    expanded.value.clear()
  } catch (e) {
    if (id === requestId) loadError.value = true
  } finally {
    if (id === requestId) loading.value = false
  }
}
onMounted(load)
onUnmounted(() => { requestId++ })
</script>

<style scoped>
.announcement-board {
  min-width: 0;
  padding: 22px 20px;
  border-radius: 18px;
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  background: var(--card);
  margin-bottom: 16px;
}
.board-heading { display: flex; gap: 11px; align-items: center; margin-bottom: 20px; }
.board-icon { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; flex-shrink: 0; background: var(--accent-soft); border-radius: 11px; color: var(--accent); }
.board-kicker { display: block; color: var(--text-muted); font-size: 8px; letter-spacing: 1.6px; margin-bottom: 5px; }
.board-heading h2 { margin: 0; font-size: 15px; font-weight: 650; color: var(--text-strong); }
.board-count { display: inline-flex; align-items: center; justify-content: center; margin-left: auto; border: 1px solid var(--border); border-radius: 7px; min-width: 22px; height: 22px; font-size: 10px; color: var(--text-muted); }
.board-list { max-height: 380px; overflow-y: auto; overscroll-behavior: contain; scrollbar-width: thin; }
.announcement-loading,
.board-empty {
  color: var(--text-muted);
  font-size: 12px;
  padding: 6px 0;
  line-height: 1.9;
}
.announcement-loading > span:not(.loading-label) { display: block; height: 9px; background: var(--accent-soft); border-radius: 4px; margin-bottom: 12px; }
.announcement-loading > span:nth-child(2) { width: 80%; }
.announcement-loading > span:nth-child(3) { width: 55%; }
.loading-label { font-size: 10px; }
.board-retry, .board-expand { display: block; padding: 5px 0; border: 0; background: transparent; color: var(--accent); font-size: 11px; cursor: pointer; }
.board-item {
  padding: 16px 0;
  border-bottom: 1px solid var(--border);
}
.board-item:first-child { padding-top: 0; }
.board-item:last-of-type {
  border-bottom: none;
}
.board-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 10px;
}
.board-author {
  color: var(--text-muted);
  max-width: 90px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.board-time {
  color: var(--text-muted);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.board-content {
  color: var(--text);
  margin: 0;
  line-height: 1.9;
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
  white-space: pre-line;
}
.board-content.expanded { display: block; -webkit-line-clamp: unset; }
.board-expand { margin-top: 5px; }
.board-more {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
  color: var(--accent);
  font-size: 11px;
  text-decoration: none;
}
@media (max-width: 860px) {
  .announcement-board { grid-column: 1 / -1; margin-bottom: 0; }
  .board-list { max-height: 220px; }
}
</style>
