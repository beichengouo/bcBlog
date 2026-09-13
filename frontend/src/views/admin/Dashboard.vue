<template>
  <div>
    <div class="stat-grid">
      <el-card v-for="item in cards" :key="item.label" class="stat-card">
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-label">{{ item.label }}</div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getStats } from '@/api/dashboard'

const stats = ref(null)

const cards = computed(() => [
  { label: '文章数', value: stats.value?.articleCount ?? 0 },
  { label: '分类数', value: stats.value?.categoryCount ?? 0 },
  { label: '标签数', value: stats.value?.tagCount ?? 0 },
  { label: '评论数', value: stats.value?.commentCount ?? 0 },
  { label: '总浏览量', value: stats.value?.viewCount ?? 0 }
])

onMounted(async () => {
  stats.value = await getStats()
})
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
}
.stat-card {
  text-align: center;
}
.stat-value {
  font-size: 30px;
  font-weight: 700;
  color: #409eff;
  line-height: 1.2;
}
.stat-label {
  margin-top: 8px;
  color: #909399;
  font-size: 14px;
}
</style>
