<template>
  <el-empty :description="(route.meta.title || '功能') + '建设中'" />
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
</script>
