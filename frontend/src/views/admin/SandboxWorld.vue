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

        <!-- 已保存的地点：一块可拖动、可缩放的区域 -->
        <div
          v-for="loc in locations"
          :key="loc.id"
          class="map-area"
          :class="{ dim: editing && form.id === loc.id }"
          :style="areaStyle(loc)"
          @pointerdown="onAreaDown($event, loc)"
        >
          <span class="area-label">
            <LocationIcon :icon="loc.icon" :size="13" />
            <span>{{ loc.name }}</span>
          </span>
          <span class="area-size">{{ loc.width }}×{{ loc.height }}</span>
          <span class="area-handle" title="拖动调整区域大小" @pointerdown.stop="onResizeDown($event, loc)"></span>
        </div>

        <!-- 正在编辑/新增的地点预览 -->
        <div
          v-if="editing"
          class="map-area preview"
          :style="areaStyle(formRect)"
          @pointerdown="onPreviewDown($event)"
        >
          <span class="area-label">
            <LocationIcon :icon="form.icon" :size="13" />
            <span>{{ form.name || '新地点' }}</span>
          </span>
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
            <el-form-item label="区域范围">
              <el-input-number v-model="form.x" :min="0" :max="100" controls-position="right" />
              <span class="range-sep">,</span>
              <el-input-number v-model="form.y" :min="0" :max="100" controls-position="right" />
              <span class="range-sep">→</span>
              <el-input-number v-model="form.x2" :min="0" :max="100" controls-position="right" />
              <span class="range-sep">,</span>
              <el-input-number v-model="form.y2" :min="0" :max="100" controls-position="right" />
              <span class="tip">左上角 → 右下角（0~100 的地图百分比）；这块范围整体算同一个地方</span>
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
        <el-table-column label="区域范围" width="200">
          <template #default="{ row }">
            ({{ row.x }}, {{ row.y }}) → ({{ (row.x || 0) + (row.width || 0) }}, {{ (row.y || 0) + (row.height || 0) }})
          </template>
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
          <span class="tip">
            该时段内不调用 AI，跨零点也支持；<strong>两个时间相同表示不启用</strong>
            （默认已关闭：角色会自己安排睡觉，由 AI 决定间隔即可）
          </span>
        </el-form-item>
        <el-form-item label="AI 决定间隔">
          <el-switch v-model="settings.aiIntervalEnabled" active-value="1" inactive-value="0" />
          <span class="tip">开启后角色每次行动时由 AI 自己决定下次隔多久（例如睡一觉就是几小时），关闭则用下方随机区间</span>
        </el-form-item>
        <el-form-item label="AI 间隔上下限">
          <el-input v-model="settings.aiIntervalMin" style="width: 90px" />
          <span class="range-sep">~</span>
          <el-input v-model="settings.aiIntervalMax" style="width: 90px" />
          <span class="tip">分钟。AI 给的间隔会被夹在这个范围内；角色可在自己的编辑里单独覆盖</span>
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
        <el-form-item label="每日记忆总结">
          <el-switch v-model="settings.memoryEnabled" active-value="1" inactive-value="0" />
          <span class="tip">每天到点把角色当天的行动总结成一段长期记忆，后续几天的活动会参考它，日志就不必长期堆积</span>
        </el-form-item>
        <el-form-item label="记忆总结时间">
          <el-input v-model="settings.memoryTime" style="width: 90px" placeholder="23:50" />
          <span class="tip">服务器时间 HH:mm，建议放在夜间静默开始之前</span>
        </el-form-item>
        <el-form-item label="提示词携带记忆">
          <el-input v-model="settings.memoryPromptDays" style="width: 90px" />
          <span class="tip">天。每次行动时把最近几天的记忆写进提示词</span>
        </el-form-item>
        <el-form-item label="总结后删当天日志">
          <el-switch v-model="settings.memoryDeleteActs" active-value="1" inactive-value="0" />
          <span class="tip">
            默认关闭：开启后前台当天的行动时间线会看不到内容（不推荐）。
            控制日志体积建议用「系统设置 → 数据清理」里的沙盒行动日志保留天数
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
  reactionCooldownMinutes: '15',
  memoryEnabled: '1',
  memoryTime: '23:50',
  memoryPromptDays: '5',
  memoryDeleteActs: '0',
  aiIntervalEnabled: '1',
  aiIntervalMin: '15',
  aiIntervalMax: '720'
})

const loading = ref(false)
const saving = ref(false)
const savingWorld = ref(false)
const savingSetting = ref(false)
const editing = ref(false)
const mapRef = ref(null)
/** 地点表单：x,y 是左上角，x2,y2 是右下角（保存时换算成区域宽高） */
const form = reactive({
  id: null,
  name: '',
  icon: 'pin',
  x: 44,
  y: 46,
  x2: 56,
  y2: 53,
  description: '',
  sortOrder: 0
})

const isCustomIcon = computed(() => /^https?:\/\//i.test(form.icon || '') || (form.icon || '').startsWith('/'))

/** 表单里的区域（左上角 + 宽高） */
const formRect = computed(() => normalizeRect(form.x, form.y, form.x2, form.y2))
const draggingId = ref(null)

let drag = null
let moved = false
let suppressMapClick = false

function clampPct(value) {
  return Math.min(100, Math.max(0, Number(value) || 0))
}

/** 由两组角点得到规范化的矩形 */
function normalizeRect(x1, y1, x2, y2) {
  const left = Math.min(clampPct(x1), clampPct(x2))
  const top = Math.min(clampPct(y1), clampPct(y2))
  return {
    left,
    top,
    width: Math.max(0, Math.abs(clampPct(x2) - clampPct(x1))),
    height: Math.max(0, Math.abs(clampPct(y2) - clampPct(y1)))
  }
}

/** 区域的显示样式（最小尺寸保证还有点击和缩放的余地） */
function areaStyle(rect) {
  if (!rect) return {}
  return {
    left: rect.left + '%',
    top: rect.top + '%',
    width: Math.max(3, rect.width) + '%',
    height: Math.max(2, rect.height) + '%'
  }
}

function rectOf(target) {
  if (target === form) {
    return { ...formRect.value }
  }
  return {
    left: target.x == null ? 50 : target.x,
    top: target.y == null ? 50 : target.y,
    width: target.width == null ? 0 : target.width,
    height: target.height == null ? 0 : target.height
  }
}

/** 把矩形写回目标（已保存的地点直接改实体字段，预览则改表单） */
function applyRect(target, rect) {
  const left = clampPct(rect.left)
  const top = clampPct(rect.top)
  const width = Math.max(0, Math.min(100 - left, rect.width))
  const height = Math.max(0, Math.min(100 - top, rect.height))
  if (target === form) {
    form.x = left
    form.y = top
    form.x2 = left + width
    form.y2 = top + height
  } else {
    target.x = Math.round(left)
    target.y = Math.round(top)
    target.width = Math.round(width)
    target.height = Math.round(height)
  }
}

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
  const x = ((event.clientX - rect.left) / rect.width) * 100
  const y = ((event.clientY - rect.top) / rect.height) * 100
  return { x: Math.min(100, Math.max(0, x)), y: Math.min(100, Math.max(0, y)) }
}

function onMapClick(event) {
  if (suppressMapClick || drag) return
  const point = percentFromEvent(event)
  // 已有未保存的表单时，点击地图把区域整体挪过去，避免误新建
  if (editing.value) {
    const rect = formRect.value
    applyRect(form, {
      left: point.x - rect.width / 2,
      top: point.y - rect.height / 2,
      width: rect.width,
      height: rect.height
    })
    return
  }
  openAdd(point.x, point.y)
}

/** 新增地点：以点击位置为中心，给一块默认区域 */
function openAdd(cx = 50, cy = 50) {
  form.id = null
  form.name = ''
  form.icon = 'pin'
  const left = clampPct(cx - 6)
  const top = clampPct(cy - 3.5)
  form.x = left
  form.y = top
  form.x2 = Math.min(100, left + 12)
  form.y2 = Math.min(100, top + 7)
  form.description = ''
  form.sortOrder = locations.value.length
  editing.value = true
}

function openEdit(row) {
  form.id = row.id
  form.name = row.name
  form.icon = row.icon || 'pin'
  const left = row.x == null ? 44 : row.x
  const top = row.y == null ? 46 : row.y
  form.x = left
  form.y = top
  form.x2 = left + (row.width == null ? 0 : row.width)
  form.y2 = top + (row.height == null ? 0 : row.height)
  form.description = row.description || ''
  form.sortOrder = row.sortOrder || 0
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  drag = null
}

async function onSaveLocation() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入地点名称')
    return
  }
  const rect = formRect.value
  if (rect.width < 1 && rect.height < 1) {
    ElMessage.warning('区域范围太小了，请拖动地图上的虚线框调整一下')
    return
  }
  saving.value = true
  try {
    await saveSandboxLocation({
      id: form.id,
      name: form.name,
      icon: form.icon,
      description: form.description,
      sortOrder: form.sortOrder,
      x: Math.round(rect.left),
      y: Math.round(rect.top),
      width: Math.round(rect.width),
      height: Math.round(rect.height)
    })
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

// 拖动地图上的区域即可移动/缩放；正在编辑时拖动的是预览区域
function startDrag(event, target, mode) {
  event.preventDefault()
  event.stopPropagation()
  drag = { target, mode, start: percentFromEvent(event), rect: rectOf(target) }
  draggingId.value = target && target.id != null ? target.id : null
  moved = false
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onAreaDown(event, loc) {
  startDrag(event, loc, 'move')
}

function onResizeDown(event, loc) {
  startDrag(event, loc, 'resize')
}

function onPreviewDown(event) {
  startDrag(event, form, 'move')
}

function onPointerMove(event) {
  if (!drag) return
  const point = percentFromEvent(event)
  const dx = point.x - drag.start.x
  const dy = point.y - drag.start.y
  if (Math.abs(dx) > 0.4 || Math.abs(dy) > 0.4) {
    moved = true
  }
  if (drag.mode === 'move') {
    applyRect(drag.target, { ...drag.rect, left: drag.rect.left + dx, top: drag.rect.top + dy })
  } else {
    applyRect(drag.target, {
      ...drag.rect,
      width: Math.max(2, drag.rect.width + dx),
      height: Math.max(1.5, drag.rect.height + dy)
    })
  }
}

async function onPointerUp() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  const state = drag
  drag = null
  draggingId.value = null
  suppressMapClick = true
  setTimeout(() => {
    suppressMapClick = false
  }, 200)
  if (!state) return
  // 预览区域只改表单，保存时一起提交
  if (state.target === form) return
  if (!moved) {
    openEdit(state.target)
    return
  }
  try {
    await saveSandboxLocation({ ...state.target })
    ElMessage.success('区域已更新')
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
/* 地点区域：半透明色块 + 虚线边框，可整体拖动，右下角手柄可缩放 */
.map-area {
  position: absolute;
  padding: 2px 4px;
  border: 1.5px dashed var(--el-color-primary);
  border-radius: 8px;
  background: rgba(64, 158, 255, 0.16);
  cursor: move;
  overflow: hidden;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.5);
}
.map-area:hover { background: rgba(64, 158, 255, 0.26); }
.map-area.dim { opacity: 0.35; }
.map-area.preview {
  border-style: dashed;
  background: rgba(255, 111, 159, 0.18);
  border-color: #ff6f9f;
  z-index: 3;
}
.area-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #2c3e50;
  background: rgba(255, 255, 255, 0.86);
  padding: 1px 6px;
  border-radius: 999px;
  white-space: nowrap;
  max-width: 100%;
  overflow: hidden;
}
.area-label svg { color: var(--el-color-primary); flex-shrink: 0; }
.area-size {
  position: absolute;
  right: 4px;
  bottom: 3px;
  font-size: 10px;
  color: #5a6b7d;
  background: rgba(255, 255, 255, 0.75);
  padding: 0 4px;
  border-radius: 999px;
}
.area-handle {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 12px;
  height: 12px;
  border-radius: 4px 0 6px 0;
  background: var(--el-color-primary);
  cursor: nwse-resize;
  opacity: 0.85;
}
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
