<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>沙盒角色</span>
        <div class="toolbar-right">
          <span class="tip">世界</span>
          <el-select v-model="selectedWorldId" size="small" style="width: 150px" @change="onSwitchWorld">
            <el-option
              v-for="item in worlds"
              :key="item.id"
              :label="item.name || ('世界 ' + item.id)"
              :value="item.id"
            >
              <div class="world-option">
                <span class="world-option-name">{{ item.name || ('世界 ' + item.id) }}</span>
                <span v-if="item.enabled !== 1" class="world-option-tag">已停止</span>
              </div>
            </el-option>
          </el-select>
          <span class="tip">共 {{ list.length }} 个角色</span>
          <el-button type="warning" plain :loading="runningAll" @click="onRunAll">全员行动一轮</el-button>
          <el-button type="primary" @click="openAdd">新增角色</el-button>
        </div>
      </div>
    </template>

    <el-alert
      class="tips"
      type="info"
      :closable="false"
      title="角色的人设写法参考酒馆角色卡：写清身份、性格、口癖、目标与禁忌，AI 才会稳定地扮演。绑定服务商与模型后，角色会按设定的间隔自动在地图上活动。"
    />

    <el-table :data="list" v-loading="loading">
      <el-table-column label="立绘" width="80">
        <template #default="{ row }">
          <img v-if="row.avatar" class="avatar-thumb" :src="row.avatar" alt="" />
          <div v-else class="avatar-empty">{{ (row.name || '?').slice(0, 1) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="140">
        <template #default="{ row }">
          <div class="char-name">{{ row.name }}</div>
          <div class="char-title">{{ row.title || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="当前位置" min-width="150">
        <template #default="{ row }">
          <div>{{ row.locationName || '尚未行动' }}</div>
          <div class="char-title">x={{ row.x }} , y={{ row.y }}</div>
        </template>
      </el-table-column>
      <el-table-column label="AI 服务商 / 模型" min-width="180">
        <template #default="{ row }">
          <div>{{ providerName(row.providerId) }}</div>
          <div class="char-title">{{ row.model || '未选择模型' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="自动行动" width="100">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled === 1"
            @change="(val) => onToggleEnabled(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="下次行动" width="170">
        <template #default="{ row }">
          <span>{{ row.enabled === 1 ? (row.nextRunTime || '待计算') : '已停用' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="战斗力" width="90">
        <template #default="{ row }">
          <span class="combat-cell">{{ row.combatPower == null ? 10 : row.combatPower }}</span>
        </template>
      </el-table-column>
      <el-table-column label="金币" width="90">
        <template #default="{ row }">
          <span class="coin-cell">{{ row.coins || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最近状态" min-width="160">
        <template #default="{ row }">
          <el-tag v-if="row.lastError" type="danger" size="small">{{ row.lastError }}</el-tag>
          <span v-else class="char-title">正常</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210">
        <template #default="{ row }">
          <el-button
            size="small"
            type="primary"
            :loading="isRunning(row.id)"
            :disabled="isRunning(row.id)"
            @click="onRun(row)"
          >
            立即执行
          </el-button>
          <el-button size="small" @click="openBackpack(row)">背包</el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="backpackVisible"
      :title="`背包 · ${backpackCharacter ? backpackCharacter.name : ''}`"
      width="min(94vw, 660px)"
    >
      <el-table :data="backpackItems" v-loading="loadingItems" size="small">
        <el-table-column label="图标" width="90">
          <template #default="{ row }">
            <el-upload
              :action="'/api/admin/upload/image'"
              :headers="uploadHeaders"
              :show-file-list="false"
              accept="image/*"
              :on-success="(res) => onItemIconSuccess(row, res)"
              :on-error="onUploadError"
            >
              <span class="item-icon-cell" :title="row.icon ? '点击更换图标' : '点击上传图标'">
                <img v-if="row.icon" :src="row.icon" alt="" />
                <template v-else>{{ emojiForItem(row.name) }}</template>
              </span>
            </el-upload>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="物品" min-width="130" />
        <el-table-column label="数量" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="1" :max="9999" size="small" @change="onUpdateItem(row)" />
          </template>
        </el-table-column>
        <el-table-column label="品质" width="120">
          <template #default="{ row }">
            <el-select v-model="row.rarity" size="small" @change="onUpdateItem(row)">
              <el-option v-for="r in ITEM_RARITIES" :key="r.value" :label="r.name" :value="r.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="170" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="onDeleteItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingItems && !backpackItems.length" description="背包是空的" :image-size="60" />

      <div class="item-add">
        <el-input v-model="newItem.name" placeholder="物品名称" style="width: 150px" maxlength="100" />
        <el-input-number v-model="newItem.quantity" :min="1" :max="9999" />
        <el-input v-model="newItem.description" placeholder="说明（可选）" style="width: 190px" maxlength="300" />
        <el-button type="primary" :loading="savingItem" @click="onAddItem">添加物品</el-button>
      </div>
      <p class="tip">
        AI 行动时会读取背包内容（提示词里已强调），并可能通过 items_change 增减物品；这里可以随时手动补充或修正。
      </p>
      <template #footer>
        <el-button @click="backpackVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="min(94vw, 680px)">
      <el-form :model="form" label-width="100px">
        <template v-if="!form.id">
          <el-form-item label="AI 一键创作">
            <div class="ai-box">
              <div class="ai-row">
                <el-select v-model="aiProviderId" placeholder="选择服务商" style="width: 190px" @change="onAiProviderChange">
                  <el-option v-for="p in providers" :key="'aip-' + p.id" :label="p.name" :value="p.id" />
                </el-select>
                <el-button :loading="aiModelLoading" @click="loadAiModels">获取模型</el-button>
                <el-select
                  v-model="aiModel"
                  filterable
                  allow-create
                  placeholder="选择模型"
                  style="width: 210px"
                >
                  <el-option v-for="m in aiModels" :key="m" :label="m" :value="m" />
                </el-select>
              </div>
              <el-input
                v-model="aiRequirement"
                type="textarea"
                :rows="3"
                maxlength="300"
                show-word-limit
                placeholder="描述你想要的角色，例如：一个在白鸦村卖花的少女，怕生但话多，随身带着一把旧口琴"
              />
              <div class="ai-row">
                <el-button type="primary" plain :loading="aiGenerating" @click="onAiGenerate">
                  AI 生成并填充
                </el-button>
                <span class="tip">会按当前世界观、地图地点和已有角色生成，填充后你还可以逐项修改</span>
              </div>
              <div v-if="draftPlace" class="draft-place">初始地点：{{ draftPlace }}</div>
              <div v-if="draftItems.length" class="draft-items">
                <span class="draft-title">初始物品：</span>
                <span v-for="(item, index) in draftItems" :key="'di-' + index" class="draft-item">
                  {{ emojiForItem(item.name) }} {{ item.name }} ×{{ item.quantity }}
                  <em>{{ rarityMeta(item.rarity).name }}</em>
                  <button type="button" title="移除这件初始物品" @click="draftItems.splice(index, 1)">×</button>
                </span>
              </div>
            </div>
          </el-form-item>
        </template>

        <el-form-item label="角色名">
          <el-input v-model="form.name" placeholder="如：魔女零" maxlength="100" />
        </el-form-item>
        <el-form-item label="称号">
          <el-input v-model="form.title" placeholder="如：流浪的星辉魔女" maxlength="100" />
        </el-form-item>
        <el-form-item label="头像 / 立绘">
          <el-upload
            :action="'/api/admin/upload/image'"
            :headers="uploadHeaders"
            :show-file-list="false"
            accept="image/*"
            :on-success="onAvatarSuccess"
            :on-error="onUploadError"
          >
            <img v-if="form.avatar" class="avatar-preview" :src="form.avatar" alt="" />
            <el-button v-else>上传图片</el-button>
          </el-upload>
          <el-button v-if="form.avatar" link type="danger" @click="form.avatar = ''">清除</el-button>
        </el-form-item>
        <el-form-item label="外貌描述">
          <el-input
            v-model="form.appearance"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="如：银白色长发，戴着一顶过大的黑色尖帽，披风上缀着星辉"
          />
        </el-form-item>
        <el-form-item label="角色人设">
          <el-input
            v-model="form.persona"
            type="textarea"
            :rows="10"
            placeholder="参考酒馆角色卡写法：身份 / 性格 / 说话方式 / 目标 / 禁忌&#10;例如：零是活了三百年的魔女，外表少女，说话懒散但心思细腻；喜欢收集星辉碎片；讨厌被人称呼为「大人」；不会主动伤害任何人。"
          />
          <div class="tip">
            <el-button link type="primary" @click="fillSample">填入示例模板（魔女零，可自行修改）</el-button>
          </div>
        </el-form-item>
        <el-form-item label="AI 服务商">
          <el-select v-model="form.providerId" placeholder="选择服务商" style="width: 220px" @change="onProviderChange">
            <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-button :loading="modelLoading" @click="loadModels">获取模型列表</el-button>
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="form.model" filterable allow-create placeholder="选择或输入模型名" style="width: 300px">
            <el-option v-for="m in models" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="采样温度">
          <el-slider v-model="temperature" :min="0" :max="2" :step="0.05" style="width: 260px" />
          <span class="tip">{{ temperature }}</span>
        </el-form-item>
        <el-form-item label="初始坐标">
          <el-input-number v-model="form.x" :min="0" :max="100" />
          <span class="range-sep">,</span>
          <el-input-number v-model="form.y" :min="0" :max="100" />
          <span class="tip">x 横向、y 纵向，0~100 的地图百分比</span>
        </el-form-item>
        <el-form-item label="行动间隔">
          <el-input-number v-model="form.intervalMin" :min="1" :max="1440" />
          <span class="range-sep">~</span>
          <el-input-number v-model="form.intervalMax" :min="1" :max="1440" />
          <span class="tip">分钟，建议 45~75；这是「AI 没给间隔」时使用的随机区间</span>
        </el-form-item>
        <el-form-item label="AI 间隔上下限">
          <el-input-number v-model="form.aiIntervalMin" :min="1" :max="10080" controls-position="right" placeholder="全局" />
          <span class="range-sep">~</span>
          <el-input-number v-model="form.aiIntervalMax" :min="1" :max="10080" controls-position="right" placeholder="全局" />
          <span class="tip">分钟。留空则用全局设置（默认 15~720）；例如给爱睡觉的角色放宽到 600，避免频繁被叫醒</span>
        </el-form-item>
        <el-form-item label="金币余额">
          <el-input-number v-model="form.coins" :min="0" :max="99999999" controls-position="right" />
        </el-form-item>
        <el-form-item label="战斗力">
          <el-input-number v-model="form.combatPower" :min="1" :max="9999" controls-position="right" />
          <span class="tip">综合实力（战斗技巧、魔力、装备），默认 10；AI 在行动里遇到学会新魔法、得到强力装备、受伤这类事件时也会自己微调</span>
          <span class="tip">角色身上的钱：AI 日常活动会赚取或消耗，前台用户也能用积分贡献</span>
        </el-form-item>
        <el-form-item label="当前状态">
          <div class="status-editor">
            <div class="status-row">
              <span class="status-label">体力</span>
              <el-input-number v-model="statusForm.体力" :min="0" :max="100" controls-position="right" />
              <span class="status-label">魔力</span>
              <el-input-number v-model="statusForm.魔力" :min="0" :max="100" controls-position="right" />
            </div>
            <div class="status-row">
              <span class="status-label">饥饿度</span>
              <el-input-number v-model="statusForm.饥饿度" :min="0" :max="100" controls-position="right" />
              <span class="status-label">心情</span>
              <el-input v-model="statusForm.心情" placeholder="如：平静" maxlength="20" style="width: 130px" />
            </div>
            <div class="status-row" v-for="(item, index) in extraStatus" :key="'extra-' + index">
              <el-input v-model="item.key" placeholder="状态名（如 精神）" style="width: 150px" maxlength="20" />
              <el-input v-model="item.value" placeholder="值（如 良好）" style="width: 150px" maxlength="30" />
              <el-button link type="danger" @click="extraStatus.splice(index, 1)">删除</el-button>
            </div>
            <el-button link type="primary" @click="extraStatus.push({ key: '', value: '' })">
              + 添加自定义状态
            </el-button>
            <div class="tip">
              体力 / 魔力 / 饥饿度 用 0~100，心情等其它项可以写文字；AI 每次行动后会自动更新这些数值，这里也可以随时手改。
            </div>
          </div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="开局剧情">
          <el-checkbox v-model="generateFirstAct">保存后立即生成第一条行动</el-checkbox>
          <span class="tip">会调用一次 AI，生成角色的第一个故事并写入行动记录；不勾选则只创建角色</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  sandboxCharacters,
  sandboxWorlds,
  saveSandboxCharacter,
  deleteSandboxCharacter,
  runSandboxCharacter,
  runAllSandboxCharacters,
  sandboxItems,
  saveSandboxItem,
  deleteSandboxItem,
  generateSandboxCharacter
} from '@/api/sandbox'
import { aiProviderList, aiProviderModels } from '@/api/ai'
import { useSandboxWorld } from '@/composables/useSandboxWorld'
import { emojiForItem, ITEM_RARITIES, rarityMeta } from '@/utils/sandboxItems'

const uploadHeaders = { Authorization: localStorage.getItem('token') || '' }

const list = ref([])
const providers = ref([])
const models = ref([])
const loading = ref(false)
const saving = ref(false)
const modelLoading = ref(false)
/** 正在执行中的角色 ID 集合：允许多个角色同时执行，各自独立转圈 */
const runningIds = ref([])
const runningAll = ref(false)
const backpackVisible = ref(false)
const backpackCharacter = ref(null)
const backpackItems = ref([])
const loadingItems = ref(false)
const savingItem = ref(false)
const newItem = reactive({ name: '', quantity: 1, description: '' })
/** AI 一键创作相关状态 */
const aiProviderId = ref(null)
const aiModel = ref('')
const aiModels = ref([])
const aiModelLoading = ref(false)
const aiRequirement = ref('')
const aiGenerating = ref(false)
const draftItems = ref([])
const draftPlace = ref('')
/** 是否在保存后立即生成第一条行动 */
const generateFirstAct = ref(true)
const dialogVisible = ref(false)
/** 当前世界（三个沙盒页面共用一个选择） */
const worlds = ref([])
const { currentWorldId, setCurrentWorld } = useSandboxWorld()
const selectedWorldId = ref(null)
const temperature = ref(0.9)
/** 标准状态项，对应 AI 提示词里的固定字段 */
const STANDARD_STATUS_KEYS = ['体力', '魔力', '饥饿度', '心情']
const statusForm = reactive({ 体力: 100, 魔力: 100, 饥饿度: 20, 心情: '平静' })
const extraStatus = ref([])

const form = reactive({
  id: null,
  name: '',
  title: '',
  avatar: '',
  appearance: '',
  persona: '',
  providerId: null,
  model: '',
  temperature: 0.9,
  x: 50,
  y: 50,
  coins: 0,
  combatPower: 10,
  intervalMin: 45,
  intervalMax: 75,
  aiIntervalMin: null,
  aiIntervalMax: null,
  enabled: 1
})

async function load() {
  loading.value = true
  try {
    list.value = await sandboxCharacters(selectedWorldId.value)
  } finally {
    loading.value = false
  }
}

/** 切换世界：与「世界与地图」「行动日志」共用同一个选择 */
async function onSwitchWorld(id) {
  setCurrentWorld(id)
  await load()
}

/** 首次进入：先取世界列表并定位到当前世界（与其它沙盒页面保持一致） */
async function initWorld() {
  worlds.value = (await sandboxWorlds()) || []
  if (!worlds.value.length) {
    selectedWorldId.value = null
    return
  }
  const stored = currentWorldId.value
  const exists = worlds.value.some((item) => item.id === stored)
  setCurrentWorld(exists ? stored : worlds.value[0].id)
  selectedWorldId.value = currentWorldId.value
}

async function loadProviders() {
  try {
    providers.value = await aiProviderList()
  } catch (e) {
    providers.value = []
  }
}

function providerName(id) {
  if (!id) return '未绑定'
  const hit = providers.value.find((p) => p.id === id)
  return hit ? hit.name : `服务商 #${id}`
}

function onAvatarSuccess(res) {
  if (res && res.code === 200) {
    form.avatar = res.data
  } else {
    ElMessage.error((res && res.msg) || '上传失败')
  }
}

/** 解析角色状态 JSON */
function parseStatus(json) {
  const result = {}
  if (!json) return result
  try {
    const obj = typeof json === 'string' ? JSON.parse(json) : json
    Object.keys(obj || {}).forEach((key) => {
      result[key] = obj[key]
    })
  } catch (e) {
    // 解析失败就当没有状态
  }
  return result
}

function numOr(value, fallback) {
  const num = Number(value)
  return Number.isFinite(num) ? num : fallback
}

/** 把表单里的状态拼成 JSON 字符串 */
function buildStatusJson() {
  const status = {
    体力: statusForm.体力,
    魔力: statusForm.魔力,
    饥饿度: statusForm.饥饿度
  }
  if (statusForm.心情) {
    status.心情 = statusForm.心情
  }
  extraStatus.value.forEach((item) => {
    const key = (item.key || '').trim()
    if (key) {
      status[key] = item.value
    }
  })
  return JSON.stringify(status)
}

function onUploadError() {
  ElMessage.error('图片上传失败，请检查文件大小和格式')
}

function openAdd() {
  form.id = null
  form.name = ''
  form.title = ''
  form.avatar = ''
  form.appearance = ''
  form.persona = ''
  form.providerId = providers.value.length ? providers.value[0].id : null
  form.model = ''
  form.temperature = 0.9
  temperature.value = 0.9
  form.x = 50
  form.y = 50
  form.coins = 0
  form.combatPower = 10
  form.intervalMin = 45
  form.intervalMax = 75
  form.aiIntervalMin = null
  form.aiIntervalMax = null
  form.enabled = 1
  statusForm.体力 = 100
  statusForm.魔力 = 100
  statusForm.饥饿度 = 20
  statusForm.心情 = '平静'
  extraStatus.value = []
  models.value = []
  // AI 一键创作的初始状态：默认沿用角色表单当前选择的服务商
  aiProviderId.value = form.providerId || (providers.value.length ? providers.value[0].id : null)
  aiModel.value = ''
  aiModels.value = []
  aiRequirement.value = ''
  draftItems.value = []
  draftPlace.value = ''
  generateFirstAct.value = true
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    title: row.title || '',
    avatar: row.avatar || '',
    appearance: row.appearance || '',
    persona: row.persona || '',
    providerId: row.providerId,
    model: row.model || '',
    temperature: Number(row.temperature || 0.9),
    x: row.x == null ? 50 : row.x,
    y: row.y == null ? 50 : row.y,
    coins: row.coins == null ? 0 : row.coins,
    intervalMin: row.intervalMin || 45,
    intervalMax: row.intervalMax || 75,
    aiIntervalMin: row.aiIntervalMin == null ? null : row.aiIntervalMin,
    aiIntervalMax: row.aiIntervalMax == null ? null : row.aiIntervalMax,
    enabled: row.enabled == null ? 1 : row.enabled
  })
  const status = parseStatus(row.statusJson)
  statusForm.体力 = numOr(status['体力'], 100)
  statusForm.魔力 = numOr(status['魔力'], 100)
  statusForm.饥饿度 = numOr(status['饥饿度'], 20)
  statusForm.心情 = status['心情'] == null ? '' : String(status['心情'])
  extraStatus.value = Object.keys(status)
    .filter((key) => !STANDARD_STATUS_KEYS.includes(key))
    .map((key) => ({ key, value: String(status[key]) }))
  temperature.value = Number(row.temperature || 0.9)
  models.value = form.model ? [form.model] : []
  dialogVisible.value = true
}

function onAiProviderChange() {
  aiModels.value = []
  aiModel.value = ''
}

async function loadAiModels() {
  if (!aiProviderId.value) {
    ElMessage.warning('请先选择 AI 服务商')
    return
  }
  aiModelLoading.value = true
  try {
    aiModels.value = await aiProviderModels(aiProviderId.value)
    if (!aiModels.value.length) {
      ElMessage.warning('没有获取到模型，可手动输入模型名')
    }
  } finally {
    aiModelLoading.value = false
  }
}

async function onAiGenerate() {
  if (!aiRequirement.value.trim()) {
    ElMessage.warning('请先描述你想要的角色')
    return
  }
  if (!aiProviderId.value || !aiModel.value) {
    ElMessage.warning('请先选择 AI 服务商和模型')
    return
  }
  aiGenerating.value = true
  try {
    const draft = await generateSandboxCharacter({
      providerId: aiProviderId.value,
      model: aiModel.value,
      requirement: aiRequirement.value.trim()
    }, selectedWorldId.value)
    applyDraft(draft)
    ElMessage.success('已生成，请检查后保存')
  } finally {
    aiGenerating.value = false
  }
}

/** 把 AI 草稿填进新增角色表单 */
function applyDraft(draft) {
  if (!draft) return
  if (draft.name) {
    form.name = draft.name
  }
  form.title = draft.title || form.title
  form.appearance = draft.appearance || form.appearance
  form.persona = draft.persona || form.persona
  form.providerId = aiProviderId.value
  form.model = aiModel.value
  if (draft.x != null) {
    form.x = draft.x
  }
  if (draft.y != null) {
    form.y = draft.y
  }
  if (draft.coins != null) {
    form.coins = draft.coins
  }
  form.locationName = draft.locationName || ''
  form.subLocation = draft.subLocation || ''
  draftPlace.value = draft.locationName
    ? draft.subLocation
      ? `${draft.locationName} · ${draft.subLocation}`
      : draft.locationName
    : ''
  const status = draft.status || {}
  statusForm.体力 = numOr(status['体力'], 100)
  statusForm.魔力 = numOr(status['魔力'], 100)
  statusForm.饥饿度 = numOr(status['饥饿度'], 20)
  statusForm.心情 = status['心情'] == null ? '平静' : String(status['心情'])
  extraStatus.value = Object.keys(status)
    .filter((key) => !STANDARD_STATUS_KEYS.includes(key))
    .map((key) => ({ key, value: String(status[key]) }))
  draftItems.value = draft.items || []
}

function fillSample() {
  form.name = form.name || '魔女零'
  form.title = form.title || '流浪的星辉魔女'
  form.appearance =
    form.appearance ||
    '银白色长发，戴着一顶过大的黑色尖帽，旅行斗篷的下摆缀着细碎的星辉，手里常拿着一本被翻旧的魔法笔记。'
  form.persona =
    form.persona ||
    [
      '身份：已经活了三百年的魔女，外表却停留在少女模样，正在这个王国里漫无目的地旅行。',
      '性格：说话懒散、爱吐槽，但对弱小的事物格外温柔；好奇心极强，看到没见过的魔法就会走不动路。',
      '说话方式：喜欢用「唔」「嘛」开头，偶尔自称「本魔女」，很少说长篇大论。',
      '目标：收集散落在各地的星辉碎片，弄清自己记忆里缺失的那一晚发生了什么。',
      '能力：擅长星光与占星魔法，也会一些治愈术；不擅长做菜。',
      '禁忌：不会伤害无辜的人；被人叫她「大人」会不高兴；讨厌下雨天赶路。'
    ].join('\n')
}

function onProviderChange() {
  models.value = []
  form.model = ''
}

async function loadModels() {
  if (!form.providerId) {
    ElMessage.warning('请先选择 AI 服务商')
    return
  }
  modelLoading.value = true
  try {
    models.value = await aiProviderModels(form.providerId)
    if (!models.value.length) {
      ElMessage.warning('没有获取到模型，可手动输入模型名')
    }
  } finally {
    modelLoading.value = false
  }
}

async function onSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入角色名')
    return
  }
  const isNew = !form.id
  saving.value = true
  try {
    const saved = await saveSandboxCharacter({
      ...form,
      // 新建角色要指明属于哪个世界；编辑时后端已有记录
      worldId: form.id ? undefined : selectedWorldId.value,
      temperature: temperature.value,
      statusJson: buildStatusJson()
    })
    let firstActSummary = ''
    if (isNew && saved && saved.id) {
      // 1) AI 生成的初始物品一起放进背包
      for (const item of draftItems.value) {
        try {
          await saveSandboxItem({
            characterId: saved.id,
            name: item.name,
            quantity: item.quantity,
            rarity: item.rarity,
            description: item.description
          })
        } catch (e) {
          ElMessage.warning(`初始物品「${item.name}」添加失败，可在背包里手动补上`)
        }
      }
      // 2) 按需生成第一条行动（开局剧情）
      if (generateFirstAct.value) {
        try {
          const act = await runSandboxCharacter(saved.id)
          firstActSummary = act.summary || act.actions || ''
        } catch (e) {
          ElMessage.warning('角色已创建，但第一条行动生成失败，可在列表里点「立即执行」重试')
        }
      }
    }
    ElMessage.success(firstActSummary ? `角色已创建，第一条行动：${firstActSummary}` : '角色已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onToggleEnabled(row, val) {
  await saveSandboxCharacter({ ...row, enabled: val ? 1 : 0 })
  row.enabled = val ? 1 : 0
  ElMessage.success(val ? '已开启该角色的自动行动' : '已停用该角色的自动行动')
  await load()
}

async function onRun(row) {
  // 同一个角色正在执行时忽略重复点击
  if (runningIds.value.includes(row.id)) {
    return
  }
  runningIds.value = [...runningIds.value, row.id]
  try {
    const act = await runSandboxCharacter(row.id)
    ElMessage.success(`执行成功：${act.summary || act.actions || '已生成新的行动'}`)
    await load()
  } catch (e) {
    // 失败信息由请求拦截器统一提示，这里只保证按钮状态恢复
  } finally {
    runningIds.value = runningIds.value.filter((id) => id !== row.id)
  }
}

/** 某个角色的「立即执行」是否正在运行 */
function isRunning(id) {
  return runningIds.value.includes(id)
}

/** 一键让所有启用角色各行动一次：多角色可以互相遇见、互动 */
async function onRunAll() {
  try {
    await ElMessageBox.confirm(
      '将让所有「启用」的角色各进行一次 AI 行动，角色会依次行动并可能互相遇见。角色较多时耗时较久，确定继续吗？',
      '全员行动一轮',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  runningAll.value = true
  try {
    const res = await runAllSandboxCharacters(selectedWorldId.value)
    const detail = (res.items || []).map((line) => `· ${line}`).join('<br/>') || '没有启用中的角色'
    await ElMessageBox.alert(
      `成功 ${res.success} 个，失败 ${res.failed} 个<br/><br/>${detail}`,
      '本轮行动结果',
      { dangerouslyUseHTMLString: true }
    ).catch(() => {})
    await load()
  } finally {
    runningAll.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(
    `确定删除角色「${row.name}」吗？它的行动记录与旅人低语也会一并删除。`,
    '提示',
    { type: 'warning' }
  )
  await deleteSandboxCharacter(row.id)
  ElMessage.success('已删除')
  await load()
}

async function openBackpack(row) {
  backpackCharacter.value = row
  backpackVisible.value = true
  newItem.name = ''
  newItem.quantity = 1
  newItem.description = ''
  await loadItems()
}

async function loadItems() {
  if (!backpackCharacter.value) return
  loadingItems.value = true
  try {
    backpackItems.value = await sandboxItems(backpackCharacter.value.id)
  } finally {
    loadingItems.value = false
  }
}

async function onAddItem() {
  if (!newItem.name.trim()) {
    ElMessage.warning('请输入物品名称')
    return
  }
  savingItem.value = true
  try {
    await saveSandboxItem({
      characterId: backpackCharacter.value.id,
      name: newItem.name.trim(),
      quantity: newItem.quantity,
      description: newItem.description
    })
    ElMessage.success('已添加')
    newItem.name = ''
    newItem.quantity = 1
    newItem.description = ''
    await loadItems()
  } finally {
    savingItem.value = false
  }
}

async function onUpdateItem(row) {
  await saveSandboxItem({
    characterId: row.characterId,
    name: row.name,
    quantity: row.quantity,
    rarity: row.rarity,
    icon: row.icon,
    description: row.description
  })
  ElMessage.success('已更新')
}

async function onItemIconSuccess(row, res) {
  if (res && res.code === 200) {
    await saveSandboxItem({
      characterId: row.characterId,
      name: row.name,
      quantity: row.quantity,
      rarity: row.rarity,
      icon: res.data,
      description: row.description
    })
    ElMessage.success('图标已更新')
    await loadItems()
  } else {
    ElMessage.error((res && res.msg) || '上传失败')
  }
}

async function onDeleteItem(row) {
  await ElMessageBox.confirm(`确定把「${row.name}」从背包里删掉吗？`, '提示', { type: 'warning' })
  await deleteSandboxItem(row.id)
  ElMessage.success('已删除')
  await loadItems()
}

onMounted(async () => {
  await initWorld()
  await loadProviders()
  await load()
})
</script>

<style scoped>
.tips { margin-bottom: 16px; }
.toolbar { display: flex; align-items: center; justify-content: space-between; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.tip { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.range-sep { margin: 0 6px; color: var(--el-text-color-secondary); }
.avatar-thumb { width: 44px; height: 44px; border-radius: 10px; object-fit: cover; }
.avatar-empty {
  width: 44px; height: 44px; border-radius: 10px; display: flex; align-items: center; justify-content: center;
  background: var(--el-fill-color-light); color: var(--el-text-color-secondary);
}
.avatar-preview { width: 90px; height: 90px; border-radius: 12px; object-fit: cover; display: block; }
.char-name { font-weight: 600; }
.char-title { color: var(--el-text-color-secondary); font-size: 12px; }
.coin-cell { color: #c9932a; font-weight: 600; }
.status-editor { width: 100%; }
.status-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.status-label { width: 48px; font-size: 13px; color: var(--el-text-color-regular); }
.item-add { display: flex; align-items: center; gap: 8px; margin-top: 14px; flex-wrap: wrap; }
.item-icon-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1px dashed var(--el-border-color);
  font-size: 18px;
  cursor: pointer;
}
.item-icon-cell img { width: 28px; height: 28px; object-fit: contain; }
.ai-box {
  width: 100%;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px dashed var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.ai-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.ai-row:last-child { margin-bottom: 0; }
.draft-place { margin-top: 6px; font-size: 13px; color: var(--el-color-primary); }
.draft-items { margin-top: 6px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 13px; }
.draft-title { color: var(--el-text-color-regular); }
.draft-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--el-border-color);
}
.draft-item em { font-style: normal; font-size: 12px; color: var(--el-text-color-secondary); }
.draft-item button {
  border: none;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0 2px;
}
.draft-item button:hover { color: var(--el-color-danger); }
.world-option { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.world-option-name { overflow: hidden; text-overflow: ellipsis; }
.world-option-tag {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 999px;
  font-size: 11px;
  color: var(--el-color-warning);
  background: rgba(230, 162, 60, 0.14);
}
.combat-cell { color: #b0416b; font-weight: 600; }
</style>
