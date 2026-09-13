<template>
  <div class="portal">
    <div class="header">bcBlog</div>
    <el-main class="content">
      <el-card v-for="a in list" :key="a.id" class="article-card" shadow="hover" @click="open(a.id)">
        <template #header>
          <div class="article-title">{{ a.title }}</div>
        </template>
        <div class="summary">{{ a.summary }}</div>
        <div class="meta">{{ a.createTime }}</div>
      </el-card>
      <el-empty v-if="!list.length" description="暂无文章" />
    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listPortalArticles } from '@/api/article'

const list = ref([])

async function load() {
  const data = await listPortalArticles({ page: 1, size: 10 })
  list.value = data.list
}

function open(id) {
  window.alert('文章详情页待实现，文章ID：' + id)
}

onMounted(load)
</script>

<style scoped>
.portal {
  min-height: 100vh;
  background: #f5f7fa;
}
.header {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  background: #fff;
}
.content {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}
.article-card {
  margin-bottom: 16px;
  cursor: pointer;
}
.article-title {
  font-weight: 600;
}
.summary {
  color: #666;
  margin-bottom: 8px;
}
.meta {
  color: #999;
  font-size: 12px;
}
</style>
