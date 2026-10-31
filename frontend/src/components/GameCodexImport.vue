<template><el-card><template #header>装备 / 遗物图鉴维护</template><p>从游戏开发工作台的“物资控制台”导出 JSON，再导入此处。成功后整批替换装备与遗物，命运牌保持独立维护。</p><input type="file" accept=".json" :disabled="busy" @change="select" /><p v-if="bundle">待导入 {{ bundle.items.length }} 项 · 导出时间 {{ bundle.exportedAt || '未知' }}</p><el-button type="primary" :disabled="!bundle || busy" :loading="busy" @click="save">确认替换图鉴</el-button><p role="status">{{ message }}</p></el-card></template>
<script setup>
import { ref } from 'vue'
import request from '@/utils/request'
const bundle = ref(null), busy = ref(false), message = ref('最大20 MiB；导入失败时保留原图鉴。')
async function select(event) {
  bundle.value = null
  const file = event.target.files?.[0]; event.target.value = ''
  if (!file) return
  busy.value = true
  try {
    if (file.size > 20 * 1024 ** 2) throw Error('文件不能超过20 MiB')
    const data = JSON.parse(await file.text())
    if (data.format !== 'pixelrogue-codex' || data.schemaVersion !== 1 || !Array.isArray(data.items) || !data.items.length) throw Error('不是有效的游戏图鉴导出文件')
    bundle.value = data; message.value = '请确认条目数量，再点击替换。'
  } catch (error) { message.value = error.message }
  finally { busy.value = false }
}
async function save() {
  busy.value = true
  try { const count = await request.post('/admin/game-packages/codex', bundle.value, { timeout: 60000 }); message.value = `已导入 ${count} 项，刷新前台图鉴即可查看。`; bundle.value = null }
  catch { message.value = '导入失败，原图鉴保持不变。' }
  finally { busy.value = false }
}
</script>
<style scoped>p { font-size: 12px; color: var(--admin-muted); line-height: 1.8; } input { display: block; margin: 16px 0; max-width: 100%; }</style>
