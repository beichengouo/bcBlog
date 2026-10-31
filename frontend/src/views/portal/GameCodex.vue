<template>
  <main class="codex-page">
    <header class="codex-header"><router-link to="/portal/games/pixelrogue">← 返回游戏主页</router-link><span>THE STARLIGHT ARCHIVE</span><h1>星途图鉴</h1><p>每一件遗珍，都藏着一段未曾熄灭的故事。</p><small>资料更新于 {{ updatedAt }} · 游戏资料快照，实际效果以所安装版本为准</small></header>
    <section class="codex-library" aria-label="图鉴资料">
      <div class="codex-tabs" role="group" aria-label="图鉴分类"><button v-for="tab in tabs" :key="tab.id" :aria-pressed="category === tab.id" @click="category = tab.id">{{ tab.name }} <small>{{ all.filter(item => item.category === tab.id).length }}</small></button></div>
      <div class="codex-toolbar"><label>搜索图鉴<input v-model="query" type="search" placeholder="名称、效果或故事…" /></label><label v-if="category !== 'fate'">品质<select v-model="rarity"><option value="all">全部品质</option><option v-for="(name, index) in rarities" :key="name" :value="String(index)">{{ name }}</option></select></label><span role="status">共 {{ filtered.length }} 项</span></div>
      <div v-if="pageItems.length" class="codex-grid" :class="{ 'is-fate': category === 'fate' }"><button v-for="item in pageItems" :key="item.category + item.id" class="codex-entry" :data-rarity="item.rarity" @click="openEntry(item)"><div class="codex-art"><img :src="item.image" :alt="item.name" loading="lazy" /></div><small>{{ category === 'fate' ? '命运牌 · 正逆双面' : `${rarities[item.rarity]} · ${kinds[item.kind]}` }}</small><h2>{{ item.name }}</h2><p>{{ item.theme || item.description }}</p><span>查看详情 ↗</span></button></div>
      <div v-else class="codex-empty"><p>没有找到匹配的条目。</p><button @click="query = ''; rarity = 'all'">清除筛选</button></div>
      <nav v-if="pages > 1" class="codex-pagination" aria-label="图鉴分页"><button :disabled="page === 1" @click="page--">上一页</button><span>{{ page }} / {{ pages }}</span><button :disabled="page === pages" @click="page++">下一页</button></nav>
    </section>
    <dialog ref="dialog" class="codex-dialog" aria-labelledby="codex-entry-title" @close="selected = null" @click="closeBackdrop">
      <template v-if="selected"><button class="codex-close" aria-label="关闭详情" @click="dialog.close()">×</button><div class="codex-detail-art"><img :src="reverse ? selected.reverseImage : selected.image" :alt="selected.name + (selected.category === 'fate' ? (reverse ? '逆位' : '正位') : '')" /></div><div class="codex-detail-copy"><span>{{ selected.category === 'fate' ? 'FATE / 命运牌' : `${rarities[selected.rarity]} · ${kinds[selected.kind]}` }}</span><h2 id="codex-entry-title">{{ selected.name }}</h2><div v-if="selected.category === 'fate'" class="codex-tabs"><button :aria-pressed="!reverse" @click="reverse = false">正位</button><button :aria-pressed="reverse" @click="reverse = true">逆位</button></div><h3>{{ selected.theme || '装备与效果' }}</h3><p>{{ selected.category === 'fate' ? (reverse ? selected.reverse : selected.upright) : selected.description }}</p><blockquote v-if="selected.flavor">{{ selected.flavor }}</blockquote></div></template>
    </dialog>
  </main>
</template>
<script setup>
import { computed, nextTick, ref, watch, onMounted } from 'vue'
import request from '@/utils/request'
import codex from '@/config/gameCodex.json'
const tabs = [{ id: 'equipment', name: '装备' }, { id: 'relic', name: '遗物' }, { id: 'fate', name: '命运牌' }]
const rarities = ['普通', '精良', '稀有', '传说'], kinds = { weapon: '武器', armor: '护甲', accessory: '饰品', buff: '遗物' }
const importedItems = ref(codex.items), updatedAt = ref(codex.updatedAt)
const all = computed(() => [...importedItems.value, ...codex.cards])
onMounted(async () => { try { const data = await request.get('/portal/game-packages/codex'); if (data?.format === 'pixelrogue-codex' && Array.isArray(data.items)) { importedItems.value = data.items; updatedAt.value = String(data.exportedAt || codex.updatedAt).slice(0,10) } } catch { /* Bundled catalogue remains available. */ } })
const category = ref('equipment'), query = ref(''), rarity = ref('all'), page = ref(1), selected = ref(null), reverse = ref(false), dialog = ref(null)
const filtered = computed(() => all.value.filter(item => item.category === category.value && (category.value === 'fate' || rarity.value === 'all' || item.rarity === Number(rarity.value)) && [item.name, item.description, item.flavor, item.theme, item.upright, item.reverse].filter(Boolean).join(' ').toLowerCase().includes(query.value.trim().toLowerCase())))
const pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / 24)))
const pageItems = computed(() => filtered.value.slice((page.value - 1) * 24, page.value * 24))
watch([category, query, rarity, importedItems], () => { page.value = 1 })
async function openEntry(item) { selected.value = item; reverse.value = false; await nextTick(); dialog.value.showModal() }
function closeBackdrop(event) { if (event.target === dialog.value) { const rect = dialog.value.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.value.close() } }
</script>
<style scoped>
.codex-page { padding: calc(var(--header-height) + 65px) 24px 80px; min-height: 100vh; background: radial-gradient(ellipse at 70% 0%, #26334d88, transparent 45%), #0b101b; color: #eee5d6; }
.codex-header, .codex-library { max-width: 1200px; margin: auto; }
.codex-header { padding: 20px 0 48px; border-bottom: 1px solid #ceb99440; }
.codex-header > a { display: block; color: #b7c2d5; font-size: 12px; margin-bottom: 38px; }
.codex-header > span { font-size: 10px; letter-spacing: 5px; color: #ceb994; }
.codex-header h1 { font-family: SimSun, serif; font-size: clamp(36px, 5vw, 60px); letter-spacing: 8px; font-weight: 400; margin: 20px 0; }
.codex-header p { font-size: 14px; color: #b6bfd0; line-height: 1.8; }
.codex-header small { font-size: 11px; color: #8e9cb3; }
.codex-tabs { display: flex; flex-wrap: wrap; gap: 12px; margin: 28px 0; }
.codex-tabs button, .codex-pagination button, .codex-empty button { border: 1px solid #ceb99444; border-radius: 8px; background: transparent; color: #b8c1d0; padding: 12px 22px; cursor: pointer; }
.codex-tabs button[aria-pressed=true] { background: #ceb9941c; color: #edd8b2; border-color: #ceb994; }
.codex-tabs small { margin-left: 12px; opacity: .65; }
.codex-toolbar { display: flex; align-items: end; flex-wrap: wrap; gap: 20px; margin-bottom: 30px; font-size: 11px; color: #9cabc1; }
.codex-toolbar label { display: grid; gap: 8px; }
.codex-toolbar input, .codex-toolbar select { border: 1px solid #8394b144; border-radius: 8px; background: #111b2b; color: #e3e7ef; padding: 12px; font: inherit; min-height: 42px; }
.codex-toolbar input { width: min(330px, 75vw); }
.codex-toolbar > span { margin-left: auto; padding-bottom: 12px; }
.codex-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 20px; }
.codex-entry { text-align: left; min-width: 0; padding: 20px; border: 1px solid #7284a33b; border-radius: 14px; background: linear-gradient(140deg,#1c293d66,#101725); color: inherit; cursor: pointer; transition: transform .2s,border-color .2s; }
.codex-entry:hover { transform: translateY(-4px); border-color: #d9c19a; }
.codex-entry[data-rarity="3"] { border-color: #b59b6055; background: radial-gradient(ellipse at top,#ceb99418,transparent 70%),#101725; }
.codex-art { height: 140px; display: grid; place-items: center; margin-bottom: 20px; }
.codex-art { overflow: hidden; min-height: 0; }
.codex-art img { display: block; width: 100%; height: 100%; min-height: 0; object-fit: contain; }
.is-fate .codex-art { height: 240px; }
.codex-entry small { color: #b9a686; font-size: 10px; }
.codex-entry h2 { font-size: 17px; font-family: SimSun,serif; font-weight: 400; margin: 12px 0; }
.codex-entry p { font-size: 11px; line-height: 1.8; color: #9eacc1; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; min-height: 40px; }
.codex-entry > span { display: block; margin-top: 16px; font-size: 10px; color: #d7c2a1; }
.codex-pagination { display: flex; align-items: center; justify-content: center; gap: 24px; margin-top: 36px; font-size: 12px; }
button:disabled { opacity: .4; cursor: default; }
.codex-empty { text-align: center; padding: 60px 0; color: #b7c2d5; }
.codex-dialog { position: fixed; margin: auto; width: min(850px,90vw); max-height: 85dvh; padding: 36px; border: 1px solid #ceb99466; border-radius: 20px; background: #111b2b; color: #eee5d6; overflow-y: auto; }
.codex-dialog[open] { display: grid; grid-template-columns: .8fr 1.2fr; gap: 30px; }
.codex-dialog::backdrop { background: #020610cc; backdrop-filter: blur(8px); }
.codex-close { position: absolute; right: 12px; top: 8px; border: 0; background: none; color: #eee5d6; font-size: 30px; cursor: pointer; }
.codex-detail-art { display: grid; place-items: center; }
.codex-detail-art img { max-width: 100%; max-height: 440px; object-fit: contain; }
.codex-detail-copy > span { color: #ceb994; font-size: 11px; }
.codex-detail-copy h2 { font-family: SimSun,serif; font-size: 30px; font-weight: 400; }
.codex-detail-copy h3 { color: #ccbda5; font-size: 13px; font-weight: 400; }
.codex-detail-copy p, .codex-detail-copy blockquote { font-size: 12px; line-height: 2; color: #b4c0d1; white-space: pre-line; }
.codex-detail-copy blockquote { margin: 24px 0 0; padding-left: 16px; border-left: 2px solid #ceb99455; color: #8f9db4; }
button:focus-visible, a:focus-visible, input:focus-visible, select:focus-visible { outline: 2px solid #ceb994; outline-offset: 3px; }
@media(max-width:900px) { .codex-grid { grid-template-columns: repeat(3,minmax(0,1fr)); } }
@media(max-width:600px) { .codex-grid { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; } .codex-entry { padding: 14px; } .codex-art { height: 110px; } .is-fate .codex-art { height: 190px; } .codex-dialog[open] { grid-template-columns: 1fr; padding: 32px 22px; } .codex-detail-art img { max-height: 260px; } .codex-tabs button { padding: 10px 14px; } }
@media(prefers-reduced-motion:reduce) { .codex-entry { transition: none; transform: none; } }
</style>
