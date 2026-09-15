<template>
  <div class="sandbox-admin">
    <el-card>
      <template #header>
        <div class="toolbar">
          <span>沙盒世界</span>
          <div class="toolbar-right">
            <span class="tip">AI 自动行动</span>
            <el-switch :model-value="settings.enabled === '1'" @change="onToggleEnabled" />
          </div>
        </div>
      </template>

      <el-alert
        class="tips"
        type="info"
        :closable="false"
        title="先上传地图背景图，再在地图预览上点击任意位置添加地点；地点坐标按百分比保存，以后换地图尺寸也不会错位。"
      />

      <el-form :model="world" label-width="100px" class="world-form">
        <el-form-item label="世界名称">
          <el-input v-model="world.name" placeholder="如：艾尔登之境" maxlength="100" />
        </el-form-item>
        <el-form-item label="世界简介">
          <el-input
            v-model="world.description"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="展示在前台地图上方的一句话介绍"
          />
        </el-form-item>
        <el-form-item label="地图背景">
          <el-upload
            :action="'/api/admin/upload/image'"
            :headers="uploadHeaders"
            :show-file-list="false"
            accept="image/*"
            :on-success="onMapSuccess"
            :on-error="onUploadError"
          >
            <img v-if="world.mapImage" class="map-thumb" :src="world.mapImage" alt="地图背景" />
            <el-button v-else>上传地图</el-button>
          </el-upload>
          <el-button v-if="world.mapImage" link type="danger" @click="world.mapImage = ''">清除地图</el-button>
          <span class="tip">建议使用横向的插画/地图图片，前端会等比例铺满</span>
        </el-form-item>
        <el-form-item label="世界设定">
          <el-input
            v-model="world.worldPrompt"
            type="textarea"
            :rows="8"
            placeholder="写给 AI 的世界观、规则与文风，例如：这是一个剑与魔法的世界，魔法需要咏唱，冒险者公会遍布各地……"
          />
          <div class="tip">这段文字会与角色人设一起交给 AI，作用等同于酒馆里的「世界书」。</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="savingWorld" @click="onSaveWorld">保存世界设置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="mt">
      <template #header>
        <div class="toolbar">
          <span>地图与地点</span>
          <div class="toolbar-right">
            <span class="tip">共 {{ locations.length }} 个地点；可拖动标记直接改坐标，点击地图空白处可新增</span>
          </div>
        </div>
      </template>

      <div ref="mapRef" class="map-preview" @click="onMapClick">
        <img v-if="world.mapImage" :src="world.mapImage" class="map-img" alt="地图背景" />
        <div v-else class="map-placeholder">还没有上传地图背景图，可以先用下方表格维护地点坐标</div>

        <!-- 多个地点挤在同一个坐标时的锚点 -->
        <div
          v-for="anchor in locationAnchors"
          :key="'lanchor-' + anchor.x + '-' + anchor.y"
          class="map-anchor"
          :style="{ left: anchor.x + '%', top: anchor.y + '%' }"
        >
          <span class="anchor-count">{{ anchor.size }}</span>
        </div>

        <!-- 已保存的地点 -->
        <div
          v-for="loc in displayLocations"
          :key="loc.id"
          class="map-marker"
          :class="{ dim: editing && form.id === loc.id }"
          :style="{ left: (draggingId === loc.id ? loc.x : loc.displayX) + '%', top: (draggingId === loc.id ? loc.y : loc.displayY) + '%' }"
          @pointerdown="onMarkerDown($event, loc)"
        >
          <span class="pin"><LocationIcon :icon="loc.icon" :size="13" /></span>
          <span class="marker-name">{{ loc.name }}</span>
        </div>

        <!-- 正在编辑/新增的地点预览：确认前先看看落点对不对 -->
        <div
          v-if="editing"
          class="map-marker preview"
          :style="{ left: form.x + '%', top: form.y + '%' }"
          @pointerdown="onMarkerDown($event, form)"
        >
          <span class="pin"><LocationIcon :icon="form.icon" :size="13" /></span>
          <span class="marker-name">{{ form.name || '新地点' }}</span>
          <span class="preview-badge">{{ form.id ? '预览' : '新增' }}</span>
        </div>
      </div>

      <!-- 地点编辑表单：直接放在地图下方，不用弹窗 -->
      <div v-if="editing" class="loc-form">
        <div class="loc-form-head">
          <strong>{{ form.id ? '编辑地点' : '新增地点' }}</strong>
          <span class="tip">地图上的虚线标记就是它的落点，可以直接拖动调整</span>
          <div class="loc-form-actions">
            <el-button size="small" @click="cancelEdit">取消</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="onSaveLocation">保存地点</el-button>
          </div>
        </div>

        <div class="loc-form-body">
          <el-form :model="form" label-width="76px" class="loc-form-left">
            <el-form-item label="名称">
              <el-input v-model="form.name" placeholder="如：晨雾森林" maxlength="100" />
            </el-form-item>
            <el-form-item label="坐标">
              <el-input-number v-model="form.x" :min="0" :max="100" controls-position="right" />
              <span class="range-sep">,</span>
              <el-input-number v-model="form.y" :min="0" :max="100" controls-position="right" />
              <span class="tip">x 横向、y 纵向（0~100 的地图百分比）</span>
            </el-form-item>
            <el-form-item label="描述">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="2"
                maxlength="500"
                placeholder="如：常年弥漫薄雾的森林，深处有会发光的萤石（会作为 AI 行动参考）"
              />
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
            </el-form-item>
          </el-form>

          <div class="loc-form-right">
            <div class="icon-title">地点图标</div>
            <div class="icon-grid">
              <button
                v-for="item in sandboxIcons"
                :key="item.key"
                type="button"
                class="icon-cell"
                :class="{ on: form.icon === item.key }"
                :title="item.name"
                @click="form.icon = item.key"
              >
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
                     stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path v-for="(d, i) in item.paths" :key="i" :d="d" />
                </svg>
              </button>
            </div>
            <div class="icon-custom">
              <el-upload
                :action="'/api/admin/upload/image'"
                :headers="uploadHeaders"
                :show-file-list="false"
                accept="image/*"
                :on-success="onIconSuccess"
                :on-error="onUploadError"
              >
                <el-button size="small">上传自定义图标</el-button>
              </el-upload>
              <template v-if="isCustomIcon">
                <img class="icon-preview" :src="form.icon" alt="自定义图标" />
                <el-button size="small" link type="danger" @click="form.icon = ''">清除</el-button>
              </template>
              <span class="tip">建议 64×64 的透明背景 PNG</span>
            </div>
          </div>
        </div>
      </div>

      <el-table :data="locations" v-loading="loading" class="mt">
        <el-table-column label="图标" width="70">
          <template #default="{ row }">
            <span class="table-icon"><LocationIcon :icon="row.icon" :size="16" /></span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="地点名称" min-width="140" />
        <el-table-column label="坐标" width="120">
          <template #default="{ row }">x={{ row.x }} , y={{ row.y }}</template>
        </el-table-column>
        <el-table-column prop="description" label="地点描述（会作为 AI 参考）" min-width="220" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDeleteLocation(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="mt" type="primary" plain @click="openAdd">新增地点</el-button>
    </el-card>

    <el-card class="mt">
      <template #header>
        <div class="toolbar">
          <span>运行参数</span>
          <div class="toolbar-right">
            <span class="tip">修改后立即生效，无需重启</span>
          </div>
        </div>
      </template>

      <el-form label-width="140px" class="setting-form">
        <el-form-item label="AI 调用开关">
          <el-switch v-model="settings.enabled" active-value="1" inactive-value="0" />
          <span class="tip">关闭后所有角色都不再自动行动（前台仍可查看历史记录）</span>
        </el-form-item>
        <el-form-item label="行动间隔（分钟）">
          <el-input v-model="settings.intervalMin" style="width: 90px" />
          <span class="range-sep">~</span>
          <el-input v-model="settings.intervalMax" style="width: 90px" />
          <span class="tip">每次行动结束后，在这个区间内随机等待下一次</span>
        </el-form-item>
        <el-form-item label="夜间静默">
          <el-input v-model="settings.nightStart" style="width: 90px" placeholder="02:00" />
          <span class="range-sep">~</span>
          <el-input v-model="settings.nightEnd" style="width: 90px" placeholder="07:00" />
          <span class="tip">该时段内不调用 AI，跨零点也支持</span>
        </el-form-item>
        <el-form-item label="每日自动行动上限">
          <el-input v-model="settings.dailyLimit" style="width: 90px" />
          <span class="tip">每个角色每天的自动调用次数上限，填 0 表示不限制</span>
        </el-form-item>
        <el-form-item label="同轮行动时间窗">
          <el-input v-model="settings.batchWindowMinutes" style="width: 90px" />
          <span class="tip">分钟内到期的角色会合并成同一轮一起行动，方便互相遇见；填 0 表示只跑已到期的角色</span>
        </el-form-item>
        <el-form-item label="互动回应链深度">
          <el-input v-model="settings.chainMaxDepth" style="width: 90px" />
          <span class="tip">
            角色与别人互动后，让对方立即行动一次的层数：0 = 关闭立即回应，1 = 只回应一轮（推荐），
            2 = 允许对方再回应一次；层级越高越容易出现连环对话，也更费 token
          </span>
        </el-form-item>
        <el-form-item label="每轮回应次数上限">
          <el-input v-model="settings.chainLimitPerRound" style="width: 90px" />
          <span class="tip">每次调度最多触发几个回应回合，防止一次性消耗过多 token</span>
        </el-form-item>
        <el-form-item label="回应冷却（分钟）">
          <el-input v-model="settings.reactionCooldownMinutes" style="width: 90px" />
          <span class="tip">刚行动过的角色在这段时间内不再被立即触发回应，避免同一角色连着说话；0 表示不限制</span>
        </el-form-item>
        <el-form-item label="AI 输出自查">
          <el-switch v-model="settings.verifyEnabled" active-value="1" inactive-value="0" />
          <span class="tip">
            开启后每次行动会额外调用一次 AI，只做 JSON 格式与数值自洽的校验修正（不改写剧情），
            能减少格式/数值错误，但 token 消耗翻倍，建议角色互动频繁时再开
          </span>
        </el-form-item>
        <el-form-item label="旅人低语消耗积分">
          <el-input v-model="settings.whisperPoints" style="width: 90px" />
          <span class="tip">前台登录用户留言一次扣除的积分，0 表示免费，管理员不扣</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="savingSetting" @click="onSaveSettings">保存运行参数</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import LocationIcon from '@/components/sandbox/LocationIcon.vue'
import { sandboxIcons } from '@/config/sandboxIcons'
import { layoutMarkers, overlapAnchors } from '@/utils/sandboxMap'
import {
  sandboxWorld,
  saveSandboxWorld,
  sandboxLocations,
  saveSandboxLocation,
  deleteSandboxLocation,
  sandboxSettings,
  saveSandboxSettings
} from '@/api/sandbox'

const uploadHeaders = { Authorization: localStorage.getItem('token') || '' }

const world = reactive({ id: null, name: '', description: '', mapImage: '', worldPrompt: '', enabled: 1 })
const locations = ref([])
const settings = reactive({
  enabled: '0',
  intervalMin: '45',
  intervalMax: '75',
  nightStart: '02:00',
  nightEnd: '07:00',
  dailyLimit: '12',
  whisperPoints: '1',
  verifyEnabled: '0',
  batchWindowMinutes: '5',
  chainMaxDepth: '1',
  chainLimitPerRound: '3',
  reactionCooldownMinutes: '15'
})

const loading = ref(false)
const saving = ref(false)
const savingWorld = ref(false)
const savingSetting = ref(false)
const editing = ref(false)
const mapRef = ref(null)
const form = reactive({ id: null, name: '', icon: 'pin', x: 50, y: 50, description: '', sortOrder: 0 })

const isCustomIcon = computed(() => /^https?:\/\//i.test(form.icon || '') || (form.icon || '').startsWith('/'))

/** 同坐标的地点自动错开，拖动时用真实坐标 */
const displayLocations = computed(() => layoutMarkers(locations.value, 5))
const locationAnchors = computed(() => overlapAnchors(displayLocations.value))
const draggingId = ref(null)

let dragging = null
let movedDuringDrag = false
let suppressMapClick = false

async function loadAll() {
  loading.value = true
  try {
    const [w, locs, s] = await Promise.all([sandboxWorld(), sandboxLocations(), sandboxSettings()])
    Object.assign(world, w || {})
    if (!world.name) world.name = ''
    locations.value = locs || []
    Object.assign(settings, s || {})
  } finally {
    loading.value = false
  }
}

async function loadLocations() {
  locations.value = await sandboxLocations()
}

function onMapSuccess(res) {
  if (res && res.code === 200) {
    world.mapImage = res.data
    ElMessage.success('地图已上传，记得点保存')
  } else {
    ElMessage.error((res && res.msg) || '上传失败')
  }
}

function onIconSuccess(res) {
  if (res && res.code === 200) {
    form.icon = res.data
    ElMessage.success('自定义图标已上传')
  } else {
    ElMessage.error((res && res.msg) || '上传失败')
  }
}

function onUploadError() {
  ElMessage.error('图片上传失败，请检查文件大小和格式')
}

async function onSaveWorld() {
  savingWorld.value = true
  try {
    await saveSandboxWorld({ ...world })
    ElMessage.success('世界设置已保存')
    await loadAll()
  } finally {
    savingWorld.value = false
  }
}

async function onToggleEnabled(val) {
  settings.enabled = val ? '1' : '0'
  await saveSandboxSettings({ ...settings })
  ElMessage.success(val ? '已开启沙盒 AI 自动行动' : '已关闭沙盒 AI 自动行动')
}

async function onSaveSettings() {
  savingSetting.value = true
  try {
    await saveSandboxSettings({ ...settings })
    ElMessage.success('运行参数已保存')
  } finally {
    savingSetting.value = false
  }
}

function percentFromEvent(event) {
  const rect = mapRef.value.getBoundingClientRect()
  const x = Math.round(((event.clientX - rect.left) / rect.width) * 100)
  const y = Math.round(((event.clientY - rect.top) / rect.height) * 100)
  return { x: Math.min(100, Math.max(0, x)), y: Math.min(100, Math.max(0, y)) }
}

function onMapClick(event) {
  if (suppressMapClick || dragging) return
  const point = percentFromEvent(event)
  // 已有未保存的表单时，点击地图只更新它的坐标，避免误新建
  if (editing.value) {
    form.x = point.x
    form.y = point.y
    return
  }
  openAdd(point.x, point.y)
}

function openAdd(x = 50, y = 50) {
  form.id = null
  form.name = ''
  form.icon = 'pin'
  form.x = x
  form.y = y
  form.description = ''
  form.sortOrder = locations.value.length
  editing.value = true
}

function openEdit(row) {
  form.id = row.id
  form.name = row.name
  form.icon = row.icon || 'pin'
  form.x = row.x
  form.y = row.y
  form.description = row.description || ''
  form.sortOrder = row.sortOrder || 0
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  dragging = null
}

async function onSaveLocation() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入地点名称')
    return
  }
  saving.value = true
  try {
    await saveSandboxLocation({ ...form })
    ElMessage.success('地点已保存')
    editing.value = false
    await loadLocations()
  } finally {
    saving.value = false
  }
}

async function onDeleteLocation(row) {
  await ElMessageBox.confirm(`确定删除地点「${row.name}」吗？`, '提示', { type: 'warning' })
  await deleteSandboxLocation(row.id)
  if (form.id === row.id) {
    cancelEdit()
  }
  ElMessage.success('已删除')
  await loadLocations()
}

// 拖动地图上的标记即可修改坐标；正在编辑时拖动的是预览标记
function onMarkerDown(event, target) {
  event.preventDefault()
  event.stopPropagation()
  dragging = target
  draggingId.value = target && target.id != null ? target.id : null
  movedDuringDrag = false
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerMove(event) {
  if (!dragging) return
  const point = percentFromEvent(event)
  dragging.x = point.x
  dragging.y = point.y
  movedDuringDrag = true
}

async function onPointerUp() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  const target = dragging
  dragging = null
  draggingId.value = null
  suppressMapClick = true
  setTimeout(() => {
    suppressMapClick = false
  }, 200)
  if (!target) return
  // 预览标记只改表单，保存时一起提交
  if (target === form) return
  if (!movedDuringDrag) {
    openEdit(target)
    return
  }
  try {
    await saveSandboxLocation({ ...target })
    ElMessage.success('坐标已更新')
  } catch (e) {
    await loadLocations()
  }
}

onMounted(loadAll)
onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
})
</script>

<style scoped>
.sandbox-admin { padding: 4px; }
.mt { margin-top: 16px; }
.tips { margin-bottom: 16px; }
.toolbar { display: flex; align-items: center; justify-content: space-between; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.tip { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.range-sep { margin: 0 8px; color: var(--el-text-color-secondary); }
.world-form { max-width: 760px; }
.setting-form { max-width: 760px; }
.map-thumb { width: 220px; border-radius: 10px; display: block; }
.map-preview {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 14px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: 1px dashed var(--el-border-color);
  cursor: crosshair;
  user-select: none;
}
.map-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.map-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.map-marker {
  position: absolute;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.18);
  cursor: grab;
  white-space: nowrap;
  font-size: 12px;
}
.map-marker.dim { opacity: 0.35; }
.map-marker.preview {
  border: 1px dashed var(--el-color-primary);
  background: rgba(255, 255, 255, 0.95);
  cursor: move;
  z-index: 3;
}
.map-marker .pin { display: inline-flex; color: var(--el-color-primary); }
.map-anchor {
  position: absolute;
  transform: translate(-50%, -50%);
  z-index: 1;
  pointer-events: none;
}
.map-anchor .anchor-count {
  display: inline-block;
  font-size: 10px;
  line-height: 14px;
  padding: 0 5px;
  border-radius: 999px;
  color: #fff;
  background: var(--el-color-primary);
  opacity: 0.85;
}
.marker-name { color: #333; }
.preview-badge {
  margin-left: 2px;
  padding: 0 6px;
  border-radius: 999px;
  font-size: 10px;
  color: #fff;
  background: var(--el-color-primary);
}

.loc-form {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--el-color-primary-light-7);
  background: var(--el-color-primary-light-9);
}
.loc-form-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.loc-form-actions { margin-left: auto; display: flex; gap: 8px; }
.loc-form-body { display: flex; gap: 24px; flex-wrap: wrap; }
.loc-form-left { flex: 1 1 420px; max-width: 640px; }
.loc-form-right { flex: 0 0 300px; }
.icon-title { font-size: 13px; color: var(--el-text-color-regular); margin-bottom: 8px; }
.icon-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 6px; }
.icon-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  background: #fff;
  color: var(--el-text-color-regular);
  cursor: pointer;
}
.icon-cell:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); }
.icon-cell.on {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.icon-custom { margin-top: 10px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.icon-preview { width: 30px; height: 30px; object-fit: contain; }
.table-icon { display: inline-flex; color: var(--el-color-primary); }
</style>
