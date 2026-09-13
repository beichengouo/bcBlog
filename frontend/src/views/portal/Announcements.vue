<template>
  <div class="announcements-page">
    <h1 class="page-title">站点公告</h1>
    <el-skeleton v-if="loading" :rows="4" animated class="skeleton" />
    <template v-else>
      <div v-for="a in list" :key="a.id" class="announcement-card">
        <div class="announcement-head">
          <span class="announcement-author">{{ a.author || '管理员' }}</span>
          <span class="announcement-time">{{ a.updateTime || a.createTime }}</span>
        </div>
        <p class="announcement-content">{{ a.content }}</p>
      </div>
      <el-empty v-if="!list.length" description="暂无公告" />
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getActiveAnnouncements } from '@/api/announcement'

const list = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    list.value = await getActiveAnnouncements()
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.announcements-page {
  max-width: 820px;
  margin: 0 auto;
  padding: calc(var(--header-height) + 24px) 20px 40px;
}
.page-title {
  font-size: 26px;
  margin: 0 0 20px;
  color: var(--text-strong);
}
.announcement-card {
  padding: 18px 20px;
  margin-bottom: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--card);
  box-shadow: var(--shadow);
}
.announcement-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  color: var(--text-muted);
  font-size: 13px;
}
.announcement-author {
  font-weight: 600;
  color: var(--accent);
}
.announcement-content {
  margin: 0;
  line-height: 1.8;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
}
.skeleton {
  margin-top: 8px;
}
@media (max-width: 768px) {
  .announcements-page {
    padding-left: 12px;
    padding-right: 12px;
  }
}
</style>
