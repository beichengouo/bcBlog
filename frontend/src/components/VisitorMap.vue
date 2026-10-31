<template>
  <el-card class="visitor-card">
    <template #header><div class="visitor-heading"><div><span>访客足迹</span><small>VISITOR ATLAS · 世界位置分布</small></div><button class="atlas-button" :disabled="loading" @click="load">刷新数据</button></div></template>
    <div class="visitor-controls"><div class="atlas-segments" role="group" aria-label="统计时间范围"><button v-for="option in ranges" :key="option.days" :aria-pressed="days === option.days" @click="days = option.days">{{ option.label }}</button></div><div class="atlas-segments" role="group" aria-label="统计指标"><button :aria-pressed="metric === 'pv'" @click="metric = 'pv'">访问次数 PV</button><button :aria-pressed="metric === 'uv'" @click="metric = 'uv'">独立 IP 数</button></div></div>
    <div class="visitor-summary"><div><span>{{ metricLabel }}</span><strong>{{ total.toLocaleString() }}</strong></div><div><span>已定位地区</span><strong>{{ points.length }}</strong></div><div><span>位置未知</span><strong>{{ unknown.toLocaleString() }}</strong></div><p>按 IP 估算城市位置<br>圆点越大，{{ metric === 'pv' ? '访问次数' : '独立 IP 数' }}越多</p></div>
    <div v-if="error" class="atlas-message" role="alert">{{ error }} <button class="atlas-button" @click="load">重试</button></div>
    <div v-if="mapError" class="atlas-message" role="alert">{{ mapError }} <button class="atlas-button" @click="loadMap">重新加载底图</button></div>
    <div class="visitor-layout"><div class="map-shell" :aria-busy="loading"><div ref="canvas" class="visitor-map" aria-label="访客世界地图，可使用鼠标拖动或加减按钮缩放"></div><div class="map-views"><button class="atlas-button" @click="setView('china')">中国视角</button><button class="atlas-button" @click="setView('world')">全球视角</button></div><div v-if="loading" class="map-status" role="status">正在读取访客足迹…</div><div v-else-if="!points.length" class="map-status" role="status">{{ total ? '当前访问尚无可定位的位置' : '所选时间范围暂无位置记录' }}</div></div><aside class="visitor-ranking"><h3>地区分布 <small>{{ metricLabel }}</small></h3><ol v-if="ranked.length"><li v-for="(point,index) in ranked" :key="point.id"><button @click="focus(point)"><span class="rank-index">{{ index + 1 }}</span><span class="rank-place">{{ point.label }}</span><strong>{{ Number(point[metric]).toLocaleString() }}</strong></button><div class="rank-track"><span :style="{ width: `${Number(point[metric]) / maxValue * 100}%` }"></span></div></li></ol><p v-else class="ranking-empty">新的访问会在这里留下足迹。</p></aside></div>
    <p v-if="data && !data.geoAvailable" class="atlas-message">离线定位库尚未就绪，访问仍会计入“位置未知”。</p>
    <footer class="visitor-note"><span>现存位置记录始于 {{ data?.collectedSince || '本功能启用后' }}，历史访问量未补录位置。独立 IP 按所选区间去重，不等同于实际人数；地区间可能重复，不能直接相加。</span><span>底图：<a href="https://www.naturalearthdata.com" target="_blank" rel="noopener noreferrer">Natural Earth</a> · <a href="https://db-ip.com" target="_blank" rel="noopener noreferrer">IP Geolocation by DB-IP</a></span></footer>
  </el-card>
</template>
<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import request from '@/utils/request'
const ranges = [{ days: 1, label: '今日' }, { days: 7, label: '近 7 天' }, { days: 30, label: '近 30 天' }]
const days = ref(7), metric = ref('pv'), data = ref(null), loading = ref(false), error = ref(''), mapError = ref(''), canvas = ref(null)
const points = computed(() => data.value?.points || [])
const ranked = computed(() => [...points.value].sort((a,b) => Number(b[metric.value])-Number(a[metric.value])).slice(0,20))
const maxValue = computed(() => Math.max(1,...points.value.map(p => Number(p[metric.value]) || 0)))
const metricLabel = computed(() => metric.value === 'pv' ? '访问次数' : '独立 IP 数')
const total = computed(() => Number(data.value?.totals?.[metric.value]) || 0)
const unknown = computed(() => Number(data.value?.totals?.[metric.value === 'pv' ? 'unknownPv' : 'unknownUv']) || 0)
let map, markers, resizeObserver, generation = 0, disposed = false
const controller = new AbortController()
function setView(view) { if (!map) return; map.fitBounds(view === 'world' ? [[-55,-170],[75,180]] : [[18,73],[54,135]],{ padding: [16,16], animate: false }) }
function focus(point) { map?.setView([point.latitude,point.longitude],5,{ animate: !window.matchMedia('(prefers-reduced-motion: reduce)').matches }) }
function draw() {
  if (!markers) return
  markers.clearLayers()
  for (const point of [...points.value].sort((a,b) => Number(b[metric.value])-Number(a[metric.value]))) {
    if (!Number.isFinite(point.latitude) || !Number.isFinite(point.longitude)) continue
    const content = document.createElement('div')
    content.textContent = `${point.label} · ${point.pv} 次访问 / ${point.uv} 个独立 IP`
    L.circleMarker([point.latitude,point.longitude],{ radius: 5 + 21*Math.sqrt(Number(point[metric.value])/maxValue.value), color: '#e3d5ff', weight: 1.5, fillColor: '#a78bfa', fillOpacity: .7 }).bindTooltip(content,{ direction: 'top' }).on('click',() => focus(point)).addTo(markers)
  }
}
async function load() {
  const current = ++generation; loading.value = true; error.value = ''; data.value = null; draw()
  try { const result = await request.get('/admin/dashboard/visitors',{ params: { days: days.value } }); if (!disposed && current === generation) { data.value = result; draw() } }
  catch { if (!disposed && current === generation) error.value = '访客统计加载失败，请稍后重试。' }
  finally { if (!disposed && current === generation) loading.value = false }
}
watch(days,load); watch(metric,draw)
onMounted(async () => {
  map = L.map(canvas.value,{ minZoom: 1, maxZoom: 8, zoomControl: false, maxBounds: [[-85,-190],[85,190]], maxBoundsViscosity: .8, attributionControl: true })
  L.control.zoom({ zoomInTitle: '放大', zoomOutTitle: '缩小' }).addTo(map)
  markers = L.layerGroup().addTo(map); setView('china')
  resizeObserver = new ResizeObserver(() => map?.invalidateSize()); resizeObserver.observe(canvas.value)
  load()
  loadMap()
})
async function loadMap() {
  mapError.value = ''
  try {
    const response = await fetch('/maps/world.geojson',{ signal: controller.signal }); if (!response.ok) throw Error()
    const world = await response.json(); if (disposed) return
    L.geoJSON(world,{ style: { color: '#596c8f', weight: .7, fillColor: '#263956', fillOpacity: 1 }, onEachFeature(feature,layer) { const label = document.createElement('span'); label.textContent = feature.properties.name; layer.bindTooltip(label) } }).addTo(map).bringToBack()
  } catch { if (!disposed) mapError.value = '地图底图加载失败，请刷新页面；访客统计仍可在右侧查看。' }}

onUnmounted(() => { disposed = true; generation++; controller.abort(); resizeObserver?.disconnect(); map?.remove(); map = null; markers = null })
</script>
<style scoped>
.visitor-heading,.visitor-controls,.visitor-summary { display:flex; justify-content:space-between; align-items:center; gap:16px; flex-wrap:wrap; }
.visitor-heading small { display:block; font-size:10px; letter-spacing:2px; color:var(--admin-muted,#8c98ad); margin-top:7px; }
.atlas-button,.atlas-segments button { background:transparent; border:1px solid #8695b448; border-radius:8px; color:inherit; padding:9px 13px; cursor:pointer; font:inherit; font-size:12px; }
.atlas-button:disabled { opacity:.5; cursor:wait; }.atlas-button:focus-visible,.atlas-segments button:focus-visible,.visitor-ranking button:focus-visible { outline:2px solid #a78bfa; outline-offset:2px; }
.atlas-segments { display:flex; gap:5px; padding:4px; background:#8090b00d; border-radius:11px; }.atlas-segments button { border-color:transparent; }.atlas-segments button[aria-pressed=true] { background:#a78bfa24; color:#9575df; border-color:#a78bfa55; }
.visitor-summary { justify-content:flex-start; margin:24px 0; gap:36px; }.visitor-summary span { display:block; color:var(--admin-muted,#8996aa); font-size:12px; }.visitor-summary strong { display:block; font-size:28px; margin-top:6px; font-variant-numeric:tabular-nums; }.visitor-summary p { margin-left:auto; color:var(--admin-muted,#8996aa); font-size:11px; line-height:1.8; }
.visitor-layout { display:grid; grid-template-columns:minmax(0,1fr) 250px; gap:20px; }.map-shell { position:relative; min-width:0; border-radius:16px; overflow:hidden; border:1px solid #657da23b; }.visitor-map { height:440px; width:100%; background:radial-gradient(ellipse at 50% 45%,#182c49,#0a1426); z-index:0; }.map-views { position:absolute; top:12px; right:12px; display:flex; gap:6px; z-index:500; }.map-views button { color:#e6defb; background:#15233ded; }.map-status { position:absolute; bottom:28px; left:50%; transform:translateX(-50%); width:max-content; max-width:85%; border:1px solid #a78bfa44; color:#d6daea; background:#15233ded; padding:10px 18px; border-radius:10px; z-index:500; font-size:12px; pointer-events:none; }
.visitor-ranking { max-height:440px; overflow:auto; }.visitor-ranking h3 { margin:4px 0 18px; font-size:14px; }.visitor-ranking h3 small { float:right; font-size:11px; font-weight:400; color:var(--admin-muted,#8996aa); }.visitor-ranking ol { padding:0; margin:0; list-style:none; }.visitor-ranking li { margin-bottom:18px; }.visitor-ranking button { display:flex; gap:9px; align-items:center; padding:0; border:0; background:none; color:inherit; cursor:pointer; width:100%; text-align:left; font-size:11px; }.rank-index { color:#a78bfa; min-width:18px; }.rank-place { flex:1; line-height:1.6; }.rank-track { height:3px; margin:7px 0 0 27px; background:#8695b420; border-radius:3px; }.rank-track span { display:block; height:100%; background:linear-gradient(90deg,#6c80ce,#b195ef); border-radius:3px; }.ranking-empty { color:var(--admin-muted,#8996aa); line-height:2; font-size:12px; }
.visitor-note { display:flex; flex-wrap:wrap; justify-content:space-between; gap:12px; font-size:11px; color:var(--admin-muted,#8996aa); line-height:1.9; margin-top:18px; }.visitor-note a { color:inherit; text-decoration:underline; }.atlas-message { font-size:12px; padding:12px 0; color:#bf8745; }
:deep(.leaflet-control-attribution) { background:#13213ade; color:#a9b9d2; }:deep(.leaflet-control-attribution a) { color:#b6a6df; }:deep(.leaflet-control-zoom a) { background:#172740; color:#e1d9f4; border-color:#4a5a78; }:deep(.leaflet-tooltip) { color:#eee7fa; background:#172740; border-color:#657497; font-size:12px; }
@media(max-width:900px) { .visitor-layout { grid-template-columns:1fr; }.visitor-ranking { max-height:220px; }.visitor-map { height:360px; }.visitor-summary { gap:20px; }.visitor-summary p { display:none; } }
</style>
