<template>
  <div class="portal">
    <header class="portal-header">
      <div class="header-inner">
        <router-link to="/portal" class="brand">bcBlog</router-link>
        <div class="search-box">
          <el-input v-model="keyword" placeholder="搜索文章..." clearable @keyup.enter="onSearch">
            <template #append>
              <el-button @click="onSearch">搜索</el-button>
            </template>
          </el-input>
        </div>
      </div>
    </header>
    <main class="portal-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const keyword = ref(route.query.keyword || '')

// 从地址栏读取关键词，保持输入框与路由同步
watch(
  () => route.query.keyword,
  (val) => {
    keyword.value = val || ''
  }
)

function onSearch() {
  const kw = keyword.value.trim()
  if (kw) {
    router.push({ path: '/portal', query: { keyword: kw } })
  } else {
    router.push({ path: '/portal' })
  }
}
</script>

<style scoped>
.portal {
  min-height: 100vh;
  background: #f5f7fa;
}
.portal-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  border-bottom: 1px solid #eee;
}
.header-inner {
  max-width: 1080px;
  margin: 0 auto;
  padding: 0 16px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.brand {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  text-decoration: none;
  white-space: nowrap;
}
.search-box {
  width: 300px;
  max-width: 60vw;
}
.portal-main {
  max-width: 1080px;
  margin: 0 auto;
  padding: 20px 16px;
}
</style>
