<template>
  <div class="game-downloads" :aria-busy="loading">
    <div class="download-heading"><h3>选择你的旅途入口</h3><span>Windows / Android</span></div>
    <p v-if="loading" role="status" class="download-message">正在读取发行信息…</p>
    <div v-else-if="failed" class="download-message" role="alert">发行信息暂时无法加载。<button type="button" @click="load">重试 ↻</button></div>
    <div v-else class="download-grid">
      <article v-for="platform in platforms" :key="platform.id" class="download-card" :class="{ 'is-available': packages[platform.id] }">
        <div class="download-emblem" aria-hidden="true">{{ platform.id === 'pc' ? '⊞' : '◇' }}</div>
        <div class="download-platform"><span>{{ platform.name }}</span><small>{{ packages[platform.id] ? packages[platform.id].version : '敬请期待' }}</small></div>
        <template v-if="packages[platform.id]">
          <p class="download-meta">{{ formatPackageSize(packages[platform.id].size) }} · {{ (packages[platform.id].uploadedAt || '').slice(0, 10) }}</p>
          <ReleaseNotes class="download-notes" :text="packages[platform.id].notes || '新的旅途，等待与你相遇。'" />
          <a class="download-button" :href="packageDownloadUrl(platform.id)" download>下载{{ platform.name }} <span aria-hidden="true">↓</span></a>
          <details><summary>文件信息与 SHA-256 校验</summary><p>{{ packages[platform.id].fileName }}</p><code>{{ packages[platform.id].sha256 }}</code></details>
        </template>
        <p v-else class="download-message">此平台安装包暂未发布。</p>
      </article>
    </div>
  </div>
</template>
<script setup>
import { formatPackageSize, packageDownloadUrl } from '@/utils/gamePackages'
import ReleaseNotes from '@/components/ReleaseNotes.vue'
const platforms = [{ id: 'pc', name: 'Windows PC版' }, { id: 'android', name: 'Android 安卓版' }]
defineProps({ packages: { type: Object, default: () => ({}) }, loading: Boolean, failed: Boolean })
const emit = defineEmits(['retry'])
function load() { emit('retry') }
</script>
<style scoped>
.game-downloads { grid-column: 1/-1; padding-top: 36px; border-top: 1px solid #ceb99426; }
.download-heading { display: flex; justify-content: space-between; gap: 20px; align-items: center; margin-bottom: 26px; }
.download-heading h3 { font-family: SimSun, serif; font-size: 23px; font-weight: 400; letter-spacing: 2px; }
.download-heading > span { color: var(--game-muted); font-size: 10px; }
.download-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 24px; }
.download-card { min-width: 0; padding: 26px; border: 1px solid #ceb99440; background: #ffffff03; }
.download-card { position: relative; overflow: hidden; border-radius: 18px; background: radial-gradient(ellipse at 100% 0%, #7084bb1a, transparent 65%), #101827; transition: border-color .3s, transform .3s; }
.download-card.is-available { border-color: #ceb99477; }
.download-card:hover { transform: translateY(-4px); border-color: #ceb994aa; }
.download-emblem { font-size: 38px; color: var(--game-gold); line-height: 1; margin-bottom: 24px; }
.download-platform { flex-wrap: wrap; }
.download-platform small { overflow-wrap: anywhere; max-width: 100%; }
.download-button { border-radius: 8px; background: linear-gradient(110deg, #ceb99420, #ceb99408); }
@media (prefers-reduced-motion: reduce) { .download-card, .download-button { transition: none; transform: none; } }
.download-platform { display: flex; justify-content: space-between; gap: 20px; color: #eadbbf; }
.download-platform small { color: var(--game-muted); font-size: 11px; }
.download-meta { font-size: 11px; color: var(--game-gold); margin: 18px 0; }
.download-notes { font-size: 12px; line-height: 2; color: var(--game-muted); max-height: 240px; overflow-y: auto; overflow-wrap: anywhere; }
.download-button { display: flex; justify-content: space-between; padding: 16px 20px; border: 1px solid #ceb99477; margin-top: 24px; font-size: 12px; color: #e4d2b0; transition: background .25s; }
.download-button:hover { background: #ceb99422; }
.download-card details { color: var(--game-muted); font-size: 10px; margin-top: 20px; }
.download-card summary { cursor: pointer; }
.download-card code,.download-card details p { display: block; overflow-wrap: anywhere; line-height: 1.9; }
.download-message { font-size: 12px; color: var(--game-muted); line-height: 1.9; padding: 16px 0; }
.download-message button { color: var(--game-gold); border: 0; background: transparent; cursor: pointer; }
@media(max-width:600px) { .download-grid { grid-template-columns: minmax(0,1fr); } .download-heading > span { display:none; } .download-card { padding:22px; } }
</style>
