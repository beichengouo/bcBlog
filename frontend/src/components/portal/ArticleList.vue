<template>
  <div class="article-list">
    <el-card v-for="a in list" :key="a.id" class="article-card" shadow="hover" @click="go(a.id)">
      <div class="article-title">{{ a.title }}</div>
      <div v-if="a.summary" class="summary">{{ a.summary }}</div>
      <div class="meta">{{ a.createTime }} · 浏览 {{ a.viewCount || 0 }}</div>
    </el-card>
    <el-empty v-if="!loading && !list.length" description="暂无文章" />
    <el-pagination
      v-if="total > size"
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="prev, pager, next"
      class="pager"
      @current-change="load"
    />
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listPortalArticles } from '@/api/article'

const props = defineProps({
  params: { type: Object, default: () => ({}) }
})

const router = useRouter()
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await listPortalArticles({ page: page.value, size: size.value, ...props.params })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function go(id) {
  router.push(`/portal/article/${id}`)
}

// 筛选条件变化时回到第一页并重新加载
watch(() => props.params, () => {
  page.value = 1
  load()
}, { deep: true })

onMounted(load)
</script>

<style scoped>
.article-card {
  margin-bottom: 16px;
  cursor: pointer;
}
.article-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
}
.summary {
  color: #666;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta {
  color: #999;
  font-size: 12px;
}
.pager {
  justify-content: center;
}
</style>
