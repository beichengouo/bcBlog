<template>
  <div class="side-card glass announcement-board">
    <div class="side-title">公告栏</div>
    <div v-if="loading" class="announcement-loading">加载中...</div>
    <template v-else>
      <div v-for="a in list" :key="a.id" class="board-item">
        <div class="board-meta">
          <span class="board-author">{{ a.author || '管理员' }}</span>
          <span class="board-time">{{ shortTime(a.updateTime || a.createTime) }}</span>
        </div>
        <div class="board-content">{{ a.content }}</div>
      </div>
      <div v-if="!list.length" class="board-empty">暂无公告</div>
    </template>
    <router-link class="board-more" to="/portal/announcements">查看全部公告 →</router-link>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getActiveAnnouncements } from '@/api/announcement'

const list = ref([])
const loading = ref(false)

function shortTime(val) {
  return val ? String(val).slice(0, 16) : ''
}

onMounted(async () => {
  loading.value = true
  try {
    const data = await getActiveAnnouncements()
    list.value = (data || []).slice(0, 5)
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.announcement-board {
  padding: 18px;
}
.announcement-loading,
.board-empty {
  color: var(--text-muted);
  font-size: 13px;
  padding: 6px 0;
}
.board-item {
  padding: 10px 0;
  border-bottom: 1px dashed var(--border);
}
.board-item:last-of-type {
  border-bottom: none;
}
.board-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 4px;
  font-size: 12px;
}
.board-author {
  color: var(--accent);
  font-weight: 600;
}
.board-time {
  color: var(--text-muted);
}
.board-content {
  color: var(--text);
  line-height: 1.65;
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.board-more {
  display: block;
  margin-top: 12px;
  text-align: right;
  color: var(--accent);
  font-size: 13px;
  text-decoration: none;
}
</style>
