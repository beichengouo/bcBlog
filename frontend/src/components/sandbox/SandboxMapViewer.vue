<template>
  <teleport to="body">
    <div v-if="visible" class="map-viewer">
      <div class="viewer-bar">
        <span class="viewer-title">{{ title || '沙盒地图' }}</span>
        <div class="viewer-actions">
          <button type="button" class="viewer-btn" title="缩小" @click="zoomAt(1 / 1.4)">−</button>
          <span class="zoom-text">{{ Math.round(scale * 100) }}%</span>
          <button type="button" class="viewer-btn" title="放大" @click="zoomAt(1.4)">＋</button>
          <button type="button" class="viewer-btn wide" @click="reset">重置</button>
          <button type="button" class="viewer-btn wide primary" @click="close">关闭</button>
        </div>
      </div>

      <div
        ref="stageRef"
        class="viewer-stage"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
        @wheel.prevent="onWheel"
        @dblclick="onDoubleClick"
        @click.self="backdropClick"
      >
        <div class="viewer-inner" :style="innerStyle">
          <img class="viewer-img" :src="mapImage" alt="地图" draggable="false" />

          <div
            v-for="loc in locations"
            :key="'vloc-' + loc.id"
            class="viewer-loc"
            :style="{ left: loc.x + '%', top: loc.y + '%' }"
          >
            <span class="viewer-loc-icon"><LocationIcon :icon="loc.icon" :size="14" /></span>
            <span class="viewer-loc-name">{{ loc.name }}</span>
          </div>

          <!-- 地点区域：默认只显示地名，点击后才展开该地的角色 -->
          <div
            v-for="loc in locations"
            :key="'varea-' + loc.id"
            class="viewer-area"
            :class="{ on: activeLocationId === loc.id }"
            :style="areaStyle(loc)"
            @click.stop="toggleLocation(loc)"
          >
            <span class="viewer-area-label">
              <LocationIcon :icon="loc.icon" :size="14" />
              <span>{{ loc.name }}</span>
            </span>
            <span v-if="charactersIn(loc).length" class="viewer-area-count">{{ charactersIn(loc).length }}</span>
          </div>

          <div v-if="activeLocation" class="viewer-actors" :style="actorsStyle" @click.stop>
            <button
              v-for="member in charactersIn(activeLocation)"
              :key="'vactor-' + member.id"
              type="button"
              class="viewer-actor"
              :class="{ active: activeId === member.id }"
              @click="onSelect(member)"
            >
              <img
                v-if="member.avatar"
                class="viewer-actor-img"
                :src="member.avatar"
                :alt="member.name"
                draggable="false"
              />
              <span v-else class="viewer-actor-img fallback">{{ (member.name || '?').slice(0, 1) }}</span>
              <span class="viewer-actor-name">{{ member.name }}</span>
            </button>
            <span v-if="!charactersIn(activeLocation).length" class="viewer-area-empty">这里暂时没有角色</span>
          </div>
        </div>
      </div>

      <p class="viewer-hint">双指缩放 / 单指拖动 · 点击地点查看该地的角色</p>
    </div>
  </teleport>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import LocationIcon from '@/components/sandbox/LocationIcon.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  mapImage: { type: String, default: '' },
  title: { type: String, default: '' },
  locations: { type: Array, default: () => [] },
  characters: { type: Array, default: () => [] },
  activeId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:visible', 'select'])

const MAX_SCALE = 6
const stageRef = ref(null)
/** 当前展开的地点（同时间只展开一个） */
const activeLocationId = ref(null)
const scale = ref(1)
const panX = ref(0)
const panY = ref(0)
const innerSize = ref({ w: 0, h: 0 })
/** 记录指针，用于区分单指拖动与双指缩放 */
const pointers = new Map()
let gesture = null
let moved = false

const innerStyle = computed(() => ({
  width: innerSize.value.w + 'px',
  height: innerSize.value.h + 'px',
  transform: `translate(${panX.value}px, ${panY.value}px) scale(${scale.value})`
}))

/** 地图按 16:9 等比缩放到全屏容器内，标记和图片保持同一坐标系 */
function measure() {
  const stage = stageRef.value
  if (!stage) return
  const width = stage.clientWidth
  const height = stage.clientHeight
  const ratio = 16 / 9
  let w = width
  let h = width / ratio
  if (h > height) {
    h = height
    w = height * ratio
  }
  innerSize.value = { w, h }
  clampPan()
}

function clampPan() {
  const stage = stageRef.value
  if (!stage) return
  const maxX = Math.max(0, (innerSize.value.w * scale.value - stage.clientWidth) / 2)
  const maxY = Math.max(0, (innerSize.value.h * scale.value - stage.clientHeight) / 2)
  panX.value = Math.min(maxX, Math.max(-maxX, panX.value))
  panY.value = Math.min(maxY, Math.max(-maxY, panY.value))
}

/** 以某个屏幕坐标为中心缩放（默认屏幕中心） */
function zoomAt(factor, clientX, clientY) {
  const stage = stageRef.value
  const next = Math.min(MAX_SCALE, Math.max(1, scale.value * factor))
  if (stage && next !== scale.value) {
    const rect = stage.getBoundingClientRect()
    const cx = (clientX == null ? rect.left + rect.width / 2 : clientX) - (rect.left + rect.width / 2)
    const cy = (clientY == null ? rect.top + rect.height / 2 : clientY) - (rect.top + rect.height / 2)
    const ratio = next / scale.value
    panX.value = cx - (cx - panX.value) * ratio
    panY.value = cy - (cy - panY.value) * ratio
  }
  scale.value = next
  clampPan()
}

function reset() {
  scale.value = 1
  panX.value = 0
  panY.value = 0
}

function close() {
  activeLocationId.value = null
  emit('update:visible', false)
}

/** 地点区域：x,y 左上角 + width,height（0 表示单点） */
function areaRect(location) {
  return {
    left: Number(location.x == null ? 50 : location.x),
    top: Number(location.y == null ? 50 : location.y),
    width: Math.max(0, Number(location.width || 0)),
    height: Math.max(0, Number(location.height || 0))
  }
}

function areaStyle(location) {
  const rect = areaRect(location)
  return {
    left: rect.left + '%',
    top: rect.top + '%',
    width: Math.max(3, rect.width) + '%',
    height: Math.max(2.2, rect.height) + '%'
  }
}

function inArea(location, x, y) {
  const rect = areaRect(location)
  if (rect.width <= 0 || rect.height <= 0) {
    return Math.abs(rect.left - x) <= 2 && Math.abs(rect.top - y) <= 2
  }
  return x >= rect.left && x <= rect.left + rect.width && y >= rect.top && y <= rect.top + rect.height
}

function locationOf(character) {
  const byName = props.locations.find((loc) => loc.name === character.locationName)
  if (byName) {
    return byName
  }
  const x = Number(character.x == null ? 50 : character.x)
  const y = Number(character.y == null ? 50 : character.y)
  return props.locations.find((loc) => inArea(loc, x, y)) || null
}

function charactersIn(location) {
  if (!location) {
    return []
  }
  return props.characters.filter((character) => {
    const hit = locationOf(character)
    return hit && hit.id === location.id
  })
}

function toggleLocation(location) {
  if (moved) {
    return
  }
  activeLocationId.value = activeLocationId.value === location.id ? null : location.id
}

const activeLocation = computed(
  () => props.locations.find((loc) => loc.id === activeLocationId.value) || null
)

/** 角色头像行贴在区域下方，靠下时改到上方 */
const actorsStyle = computed(() => {
  const location = activeLocation.value
  if (!location) {
    return {}
  }
  const rect = areaRect(location)
  const below = rect.top + rect.height < 68
  return {
    left: Math.min(70, Math.max(0, rect.left - 2)) + '%',
    top: (below ? rect.top + Math.max(rect.height, 2.2) : rect.top) + '%',
    transform: below ? 'translateY(8px)' : 'translateY(calc(-100% - 8px))'
  }
})

function backdropClick() {
  // 拖动地图结束时的点击不要误关全屏
  if (moved) return
  close()
}

function onSelect(character) {
  // 拖动地图时不要误触发角色点击
  if (moved) return
  activeLocationId.value = null
  emit('select', character)
}

function onPointerDown(event) {
  const stage = stageRef.value
  if (!stage) return
  stage.setPointerCapture?.(event.pointerId)
  pointers.set(event.pointerId, { x: event.clientX, y: event.clientY })
  moved = false
  if (pointers.size === 1) {
    gesture = {
      type: 'pan',
      startX: event.clientX,
      startY: event.clientY,
      panX: panX.value,
      panY: panY.value
    }
  } else if (pointers.size === 2) {
    const [a, b] = Array.from(pointers.values())
    gesture = {
      type: 'pinch',
      startDist: Math.hypot(a.x - b.x, a.y - b.y) || 1,
      startScale: scale.value,
      startPanX: panX.value,
      startPanY: panY.value,
      midX: (a.x + b.x) / 2,
      midY: (a.y + b.y) / 2
    }
  }
}

function onPointerMove(event) {
  if (!pointers.has(event.pointerId) || !gesture) return
  pointers.set(event.pointerId, { x: event.clientX, y: event.clientY })

  if (gesture.type === 'pan' && pointers.size === 1) {
    const dx = event.clientX - gesture.startX
    const dy = event.clientY - gesture.startY
    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) {
      moved = true
    }
    panX.value = gesture.panX + dx
    panY.value = gesture.panY + dy
    clampPan()
    return
  }

  if (gesture.type === 'pinch' && pointers.size === 2) {
    const [a, b] = Array.from(pointers.values())
    const distance = Math.hypot(a.x - b.x, a.y - b.y) || 1
    const midX = (a.x + b.x) / 2
    const midY = (a.y + b.y) / 2
    const next = Math.min(MAX_SCALE, Math.max(1, gesture.startScale * (distance / gesture.startDist)))
    scale.value = next
    panX.value = gesture.startPanX + (midX - gesture.midX)
    panY.value = gesture.startPanY + (midY - gesture.midY)
    moved = true
    clampPan()
  }
}

function onPointerUp(event) {
  pointers.delete(event.pointerId)
  if (pointers.size === 0) {
    gesture = null
    return
  }
  // 从双指回到单指时，以当前状态重新作为拖动起点
  const [only] = Array.from(pointers.values())
  gesture = {
    type: 'pan',
    startX: only.x,
    startY: only.y,
    panX: panX.value,
    panY: panY.value
  }
}

function onWheel(event) {
  zoomAt(event.deltaY < 0 ? 1.15 : 1 / 1.15, event.clientX, event.clientY)
}

function onDoubleClick(event) {
  // 双击在放大与重置之间切换
  if (scale.value > 1.05) {
    reset()
  } else {
    zoomAt(2, event.clientX, event.clientY)
  }
}

function onKeydown(event) {
  if (event.key === 'Escape' && props.visible) {
    close()
  }
}

watch(
  () => props.visible,
  (value) => {
    if (value) {
      reset()
      activeLocationId.value = null
      document.body.style.overflow = 'hidden'
      window.addEventListener('resize', measure)
      // 等 DOM 渲染完成再量尺寸
      requestAnimationFrame(measure)
    } else {
      document.body.style.overflow = ''
      window.removeEventListener('resize', measure)
      pointers.clear()
      gesture = null
    }
  }
)

if (typeof window !== 'undefined') {
  window.addEventListener('keydown', onKeydown)
}

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKeydown)
    window.removeEventListener('resize', measure)
  }
  document.body.style.overflow = ''
})
</script>

<style scoped>
.map-viewer {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  background: rgba(8, 8, 16, 0.92);
  backdrop-filter: blur(6px);
}
.viewer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  padding-top: calc(10px + env(safe-area-inset-top, 0px));
  color: #fff;
  flex-wrap: wrap;
}
.viewer-title { font-size: 15px; font-weight: 600; }
.viewer-actions { display: flex; align-items: center; gap: 8px; }
.viewer-btn {
  min-width: 36px;
  height: 34px;
  padding: 0 10px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 15px;
  cursor: pointer;
}
.viewer-btn.wide { font-size: 13px; }
.viewer-btn.primary { background: rgba(255, 111, 159, 0.85); border-color: transparent; }
.zoom-text { min-width: 46px; text-align: center; font-size: 12px; opacity: 0.85; }

.viewer-stage {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  /* 交给脚本处理手势，避免浏览器把拖动当成页面滚动 */
  touch-action: none;
  cursor: grab;
}
.viewer-stage:active { cursor: grabbing; }
.viewer-inner {
  position: relative;
  transform-origin: center center;
  will-change: transform;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 10px;
  overflow: hidden;
}
.viewer-img { width: 100%; height: 100%; object-fit: cover; display: block; }

.viewer-anchor {
  position: absolute;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  gap: 2px;
  pointer-events: none;
  z-index: 1;
}
.anchor-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.3);
}
.anchor-count {
  font-size: 10px;
  padding: 0 5px;
  border-radius: 999px;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
}

.viewer-loc {
  position: absolute;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  pointer-events: none;
}
.viewer-loc-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50% 50% 50% 6px;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.7);
}
.viewer-loc-name {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 999px;
  color: #fff;
  background: rgba(0, 0, 0, 0.4);
  white-space: nowrap;
}

/* 地点区域：默认只显示地名，点开后下方浮出角色头像 */
.viewer-area {
  position: absolute;
  padding: 2px 4px;
  border: 1.5px dashed rgba(255, 255, 255, 0.8);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.16);
  cursor: pointer;
  overflow: hidden;
  transition: background 0.25s ease, border-color 0.25s ease;
}
.viewer-area.on { border-color: #ff6f9f; background: rgba(255, 111, 159, 0.24); }
.viewer-area-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  white-space: nowrap;
  overflow: hidden;
}
.viewer-area-count {
  position: absolute;
  right: 4px;
  bottom: 3px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  color: #fff;
  background: linear-gradient(120deg, #ff6f9f, #a06bd8);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.35);
}
.viewer-actors {
  position: absolute;
  z-index: 6;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  max-width: 70%;
  padding: 6px 8px;
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(6px);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.38);
}
.viewer-area-empty { font-size: 12px; color: rgba(255, 255, 255, 0.75); }

.viewer-actor {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  cursor: pointer;
  padding: 0;
  border: none;
  background: transparent;
}
.viewer-actor.active .viewer-actor-img { box-shadow: 0 0 0 3px #ff6f9f, 0 8px 20px rgba(0, 0, 0, 0.4); }
.viewer-actor-img {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba(255, 255, 255, 0.9);
  background: #fff;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #333;
  font-size: 17px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.viewer-actor:hover .viewer-actor-img { transform: translateY(-2px); }
.viewer-actor-name {
  font-size: 12px;
  padding: 1px 9px;
  border-radius: 999px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  white-space: nowrap;
}

.viewer-hint {
  margin: 0;
  padding: 10px 14px calc(12px + env(safe-area-inset-bottom, 0px));
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

@media (max-width: 720px) {
  .viewer-actor-img { width: 44px; height: 44px; font-size: 17px; }
  .viewer-title { font-size: 14px; }
}
</style>
