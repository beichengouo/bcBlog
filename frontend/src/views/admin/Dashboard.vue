<template>
  <el-card>
    <template #header>欢迎使用 bcBlog</template>
    <p>当前登录账号：{{ userStore.userInfo?.username }}</p>
    <p>昵称：{{ userStore.userInfo?.nickname }}</p>
    <p>这是前后端分离博客项目的基础骨架，功能将逐步完善。</p>
  </el-card>
</template>

<script setup>
import { onMounted } from 'vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

onMounted(() => {
  userStore.fetchInfo().catch(() => {})
})
</script>
