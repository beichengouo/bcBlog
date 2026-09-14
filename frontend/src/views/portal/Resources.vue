<template>
  <div class="resources-page">
    <header class="page-head">
      <h1 class="page-title">智库</h1>
      <p class="page-sub">整理收藏的资源链接，需要时可以直接取用</p>
    </header>

    <div v-if="loading" class="loading">加载中...</div>
    <el-empty v-else-if="!list.length" description="智库暂时还是空的" />
    <div v-else class="resource-grid">
      <article v-for="r in list" :key="r.id" class="resource-card glass" @click="goDetail(r)">
        <div class="cover">
          <img v-if="r.cover" :src="r.cover" alt="" />
          <div v-else class="cover-fallback">资源</div>
          <span v-if="r.unlocked" class="unlocked-tag">已解锁</span>
        </div>
        <div class="body">
          <h3 class="resource-title">{{ r.title }}</h3>
          <p v-if="r.description" class="resource-desc">{{ r.description }}</p>
          <div class="meta">
            <span class="points">{{ r.points > 0 ? r.points + ' 积分' : '免费' }}</span>
            <span class="detail-link">查看详情 →</span>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { portalResourceList } from '@/api/resource'

const router = useRouter()
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

function goDetail(resource) {
  router.push(`/portal/resources/${resource.id}`)
}

onMounted(load)
</script>

<style scoped>
.resources-page {
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
.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 18px;
}
.resource-card {
  border-radius: var(--radius);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
.resource-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-hover);
}
.cover {
  position: relative;
  aspect-ratio: 16 / 9;
  background: var(--accent-soft);
}
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  font-weight: 700;
  background:
    radial-gradient(circle at 20% 20%, var(--accent-soft), transparent 60%),
    radial-gradient(circle at 80% 80%, var(--accent-soft), transparent 60%);
}
.unlocked-tag {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
  background: #67c23a;
}
.body {
  padding: 14px 16px;
}
.resource-title {
  margin: 0 0 8px;
  font-size: 17px;
  color: var(--text-strong);
}
.resource-desc {
  margin: 0 0 12px;
  color: var(--text-muted);
  font-size: 14px;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}
.points {
  color: var(--accent);
  font-weight: 600;
}
.detail-link {
  color: var(--text-muted);
}
</style>
