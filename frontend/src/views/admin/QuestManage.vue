<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>旅人委托板</span>
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
          <el-button type="primary" :loading="generating" @click="onGenerate">立即生成一批</el-button>
          <el-button @click="openAdd">手动新增</el-button>
        </div>
      </div>
    </template>

    <el-alert
      class="tips"
      type="info"
      :closable="false"
      title="角色行动时会看到「最新一批可接」的委托（带距离），自己决定接哪一条；接下后变成 TA 的「当前委托」，每步由 AI 汇报进度。进度到 100 且通过服务端两道校验（讨伐/护送/探索类要真的在目标地区，采集/杂活类要真的拿到东西）才算完成，奖励直接进角色的账。已完成的委托会在委托板与角色档案里保留 3 天，之后由数据清理删除。"
    />
    <el-alert
      class="tips"
      type="info"
      :closable="false"
      title="完成一律由角色自己走出来：没有「强制完成」按钮（它会绕过两道校验、还可能和角色正在执行的那一步抢着结算，容易重复发奖励）。卡住的委托就用「下架」把它撤下来——会对「接取中」的也生效，同时解除接取关系（进度清零、角色下一步重新看到委托列表），并且不发放任何奖励。"
    />
    <el-alert
      class="tips"
      type="warning"
      :closable="false"
      title="关于「在板上」与「已下架」：委托板是整批换的——生成新一批时，上一批里「还没人接、没被置顶、且是 AI 生成的」委托会被换下（状态变成「已下架」，标着「不在板上」），前台看不到、角色也不会再接；想留住某一条就给它「置顶」，置顶的刷新时不会被换下。被换下的委托会保留 3 天供你查看或「重新上板」，之后由数据清理删除。所以按「每次生成条数 = 5」生成，板上就是 5 条。"
    />

    <div class="stat-row">
      <el-tag type="primary" effect="plain">可接 {{ stats.open || 0 }}</el-tag>
      <el-tag type="warning" effect="plain">接取中 {{ stats.taken || 0 }}</el-tag>
      <el-tag type="success" effect="plain">已完成 {{ stats.completed || 0 }}</el-tag>
      <el-tag v-if="stats.expired" type="info" effect="plain">已下架 {{ stats.expired }}</el-tag>
      <el-tag effect="plain">当前批次 {{ batchText }}</el-tag>
    </div>

    <div class="filter-row">
      <span class="filter-label">范围</span>
      <el-radio-group v-model="scopeFilter" size="small" @change="onSwitchScope">
        <el-radio-button :value="'board'">当前板面</el-radio-button>
        <el-radio-button :value="'all'">全部（含历史）</el-radio-button>
      </el-radio-group>
      <span class="filter-label">状态</span>
      <el-radio-group v-model="statusFilter" size="small" @change="load">
        <el-radio-button :value="'all'">全部</el-radio-button>
        <el-radio-button :value="'open'">可接</el-radio-button>
        <el-radio-button :value="'taken'">接取中</el-radio-button>
        <el-radio-button :value="'completed'">已完成</el-radio-button>
        <el-radio-button v-if="scopeFilter === 'all'" :value="'expired'">已下架</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="quests" v-loading="loading" size="small" class="mt">
      <el-table-column label="委托" min-width="240">
        <template #default="{ row }">
          <div class="quest-cell">
            <span class="quest-emoji">{{ typeEmoji(row.questType) }}</span>
            <div>
              <div class="quest-title">
                {{ row.title }}
                <el-tag v-if="row.pinned === 1" size="small" effect="plain" type="warning">置顶</el-tag>
                <el-tag v-if="row.source === 'ai'" size="small" effect="plain">AI</el-tag>
              </div>
              <div class="quest-sub">
                {{ typeText(row.questType) }} · 难度 {{ difficultyStars(row.difficulty) }}
                <template v-if="row.locationName"> · {{ row.locationName }}</template>
              </div>
              <div class="quest-desc">{{ row.target || row.description || '（没有目标描述）' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="对手战力" width="90">
        <template #default="{ row }">
          <span v-if="row.power">{{ row.power }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="报酬" width="180">
        <template #default="{ row }">
          <div class="reward-chips">
            <span v-if="row.rewardCoins" class="rw-coin">✦ {{ row.rewardCoins }}</span>
            <span
              v-for="(r, i) in row.rewards || []"
              :key="'r-' + row.id + '-' + i"
              class="rw-item"
              :style="{
                color: rarityMeta(r.rarity).color,
                borderColor: rarityMeta(r.rarity).border,
                background: rarityMeta(r.rarity).bg
              }"
            >
              {{ emojiForItem(r.name) }} {{ r.name }}<template v-if="(r.quantity || 1) > 1">×{{ r.quantity }}</template>
            </span>
            <span v-if="!row.rewardCoins && !(row.rewards || []).length" class="muted">无</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="210">
        <template #default="{ row }">
          <el-tag size="small" :type="statusMeta(row.status).type" effect="plain">
            {{ statusMeta(row.status).text }}
          </el-tag>
          <!-- 明确告诉管理员"这条现在到底在不在板上"：状态和"在不在板上"是两件事 -->
          <el-tag v-if="row.onBoard" size="small" type="success" effect="dark" class="board-tag">在板上</el-tag>
          <el-tag v-else size="small" type="info" effect="plain" class="board-tag">不在板上</el-tag>
          <div v-if="row.status === 'taken'" class="quest-sub">{{ row.takerName }} 接取中</div>
          <div v-else-if="row.status === 'completed'" class="quest-sub">已被 {{ row.takerName || '某人' }} 完成</div>
          <div v-else-if="!row.onBoard" class="quest-sub">
            {{ row.status === 'expired' ? '刷新委托板时被换下' : '旧批次，已被新一批取代' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="150">
        <template #default="{ row }">
          <el-progress :percentage="row.progress || 0" :stroke-width="8" />
          <div v-if="row.progressNote" class="quest-sub">{{ row.progressNote }}</div>
        </template>
      </el-table-column>
      <el-table-column label="置顶" width="90">
        <template #default="{ row }">
          <el-switch :model-value="row.pinned === 1" @change="onTogglePinned(row, $event)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="340">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <!-- 改进度只对「接取中」开放：没人接的委托一被接取，进度就会从头开始算 -->
          <el-button v-if="row.status === 'taken'" size="small" @click="openProgress(row)">改进度</el-button>
          <!-- 在板上没人接、或正被某人执行 → 可以撤下来；不在板上 → 可以放回去 -->
          <el-button
            v-if="(row.status === 'open' && row.onBoard) || row.status === 'taken'"
            size="small"
            @click="onExpire(row)"
          >
            下架
          </el-button>
          <el-button
            v-else-if="row.status === 'open' || row.status === 'expired'"
            size="small"
            type="primary"
            plain
            @click="onReset(row)"
          >
            重新上板
          </el-button>
          <el-button v-else-if="row.status === 'completed'" size="small" @click="onReset(row)">
            重置回可接
          </el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !quests.length" description="还没有委托，可以点右上角生成一批" :image-size="70" />

    <!-- 生成与刷新设置 -->
    <el-divider content-position="left">生成设置</el-divider>
    <el-form :model="settings" label-width="150px" class="setting-form">
      <el-form-item label="委托板开关">
        <el-switch v-model="settings.questEnabled" active-value="1" inactive-value="0" />
        <span class="tip">关闭后前台不显示、角色也不再接取（数据保留）</span>
      </el-form-item>
      <el-form-item label="前台栏目名">
        <el-input v-model="settings.questTitle" style="width: 200px" maxlength="20" />
      </el-form-item>
      <el-form-item label="每次生成条数">
        <el-input v-model="settings.questPerGenerate" style="width: 90px" />
        <span class="tip">
          1~10 条。刷新时<b>接取中的委托会保留</b>，只补足到这个数量
          （例：设 4、已有 1 条被接取 → 只新生成 3 条）
        </span>
      </el-form-item>
      <el-form-item label="自动刷新">
        <el-switch v-model="settings.questAutoEnabled" active-value="1" inactive-value="0" />
        <span class="tip">开启后按下面的间隔自动换一批委托（接取中的会保留）</span>
      </el-form-item>
      <el-form-item label="刷新间隔 / 首次时间">
        <el-input v-model="settings.questIntervalHours" style="width: 90px" />
        <span class="range-sep">小时 · 当天首次</span>
        <el-input v-model="settings.questAutoTime" style="width: 90px" placeholder="09:00" />
        <span class="tip">
          间隔填 24 就是每天一次；填 6 就是 09:00 / 15:00 / 21:00 / 03:00 一天四次
        </span>
      </el-form-item>
      <el-form-item label="提示词里列几条">
        <el-input v-model="settings.questVisibleCount" style="width: 90px" />
        <span class="tip">角色每次行动最多看到几条可接委托（1~12，默认 8）</span>
      </el-form-item>
      <el-form-item label="单步进度上限">
        <el-input v-model="settings.questProgressStepMax" style="width: 90px" />
        <span class="tip">
          %。<b>兜底值</b>：下面那行分档没配、或某一档写坏了，就用这个单值顶上
        </span>
      </el-form-item>
      <el-form-item label="按难度分档">
        <el-input v-model="settings.questStepMaxByDifficulty" style="width: 240px" placeholder="100,70,50,35,20" />
        <span class="tip">
          单步进度上限（%），按<b>难度 1~5</b> 依次填写。默认 <b>100 / 70 / 50 / 35 / 20</b>：
          难度 1 的杂活允许一步做完（清点库房这种小事不该硬拖两天），难度越高越"磨"。
          填几档就按几档算，写坏的某一档会用上面的兜底值顶替
        </span>
      </el-form-item>
      <el-form-item label="被拦下时自检">
        <el-switch v-model="settings.questSelfcheck" active-value="1" inactive-value="0" />
        <span class="tip">
          完成校验没过时（例如人还没到目标地区），再调一次 AI 把"其实没完成"改回来：
          会重写这一步的动作叙述和进度，但<b>不允许</b>改地点、也不允许写"已经拿到报酬"（默认开启）
        </span>
      </el-form-item>
      <el-form-item label="生成服务商 / 模型">
        <el-select v-model="settings.questProviderId" clearable placeholder="留空用系统服务商" style="width: 200px">
          <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="String(p.id)" />
        </el-select>
        <el-select
          v-model="settings.questModel"
          filterable
          allow-create
          clearable
          :loading="modelLoading"
          placeholder="选择或输入模型"
          style="width: 240px; margin-left: 8px"
        >
          <el-option v-for="m in models" :key="m" :label="m" :value="m" />
        </el-select>
        <el-button style="margin-left: 8px" :loading="modelLoading" @click="loadModels">获取模型</el-button>
        <span class="tip">从所选服务商拉取模型列表；也可以直接手填模型名</span>
      </el-form-item>
      <el-form-item label="附加要求">
        <el-input
          v-model="settings.questPromptExtra"
          type="textarea"
          :rows="2"
          style="width: 520px"
          maxlength="300"
          placeholder="例如：多一些采集与护送类，少一些讨伐；委托难度整体偏低"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="savingSetting" @click="onSaveSettings">保存设置</el-button>
      </el-form-item>
    </el-form>

    <!-- 手动新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑委托' : '手动新增委托'" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="60" placeholder="如：清除晨雾森林的影狼" />
        </el-form-item>
        <el-form-item label="类型 / 难度">
          <el-select v-model="form.questType" style="width: 140px">
            <el-option v-for="t in QUEST_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-select v-model="form.difficulty" style="width: 160px; margin-left: 8px">
            <el-option v-for="d in [1, 2, 3, 4, 5]" :key="d" :label="difficultyStars(d)" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标地区">
          <el-select v-model="form.locationName" clearable placeholder="从地图地点里选" style="width: 260px">
            <el-option v-for="l in locations" :key="l.id" :label="l.name" :value="l.name" />
          </el-select>
          <span class="tip">选填；讨伐/护送/探索类会校验"人必须真的到过这里"</span>
        </el-form-item>
        <el-form-item label="目标">
          <el-input v-model="form.target" maxlength="200" placeholder="清除 3 只影狼并带回狼牙" />
        </el-form-item>
        <el-form-item label="对手战力">
          <el-input-number v-model="form.power" :min="0" :max="9999" controls-position="right" />
          <span class="tip">讨伐类填；保存时会自动夹到该地点的战力区间内</span>
        </el-form-item>
        <el-form-item label="奖励金币">
          <el-input-number v-model="form.rewardCoins" :min="0" :max="500" controls-position="right" />
        </el-form-item>
        <el-form-item label="奖励物品">
          <div class="reward-editor">
            <div v-for="(r, i) in form.rewards" :key="'fr-' + i" class="reward-item">
              <!-- 每件奖励拆成三行：名字独占一行，品质+数量一行，说明一行 ——
                   这样无论弹窗多窄都不会把输入框挤出容器 -->
              <div class="reward-row">
                <el-input v-model="r.name" class="rw-name" maxlength="30" placeholder="物品名，如：治疗药水" />
                <el-button link type="danger" class="rw-del" @click="form.rewards.splice(i, 1)">删除</el-button>
              </div>
              <div class="reward-row">
                <span class="rw-label">品质</span>
                <el-select v-model="r.rarity" class="rw-rarity">
                  <el-option v-for="q in ITEM_RARITIES" :key="q.value" :label="q.name" :value="q.value">
                    <span class="rarity-dot" :style="{ background: q.color }"></span>{{ q.name }}
                  </el-option>
                </el-select>
                <span class="rw-label">数量</span>
                <el-input-number v-model="r.quantity" class="rw-qty" :min="1" :max="9" controls-position="right" />
              </div>
              <el-input
                v-model="r.description"
                class="rw-desc"
                maxlength="40"
                placeholder="一句说明（会写进背包里的物品描述）"
              />
            </div>
            <el-button size="small" @click="form.rewards.push({ name: '', rarity: 1, quantity: 1, description: '' })">
              添加奖励物品
            </el-button>
            <div class="tip">最多 5 件；品质的配色和集市、角色背包是同一套</div>
          </div>
        </el-form-item>
        <el-form-item label="详情">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="委托人是谁、为什么要做、有什么注意事项"
          />
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="form.pinned" :active-value="1" :inactive-value="0" />
          <span class="tip">置顶的排在委托板最前面，而且刷新委托板时不会被换下</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 手动改进度 -->
    <el-dialog v-model="progressVisible" title="调整委托进度" width="460px">
      <el-form label-width="90px">
        <el-form-item label="委托">
          <span>{{ progressForm.title }}</span>
        </el-form-item>
        <el-form-item label="进度">
          <el-input-number v-model="progressForm.progress" :min="0" :max="99" controls-position="right" />
          <span class="tip">
            只能往上调，最多 99%：最后 1% 要由角色自己走完（到没到目标地区、有没有真拿到东西都要校验），
            这样奖励才不会在角色没参与的情况下发出去
          </span>
        </el-form-item>
        <el-form-item label="进度说明">
          <el-input v-model="progressForm.note" maxlength="200" placeholder="会展示在角色档案里" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingProgress" @click="onSaveProgress">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSandboxWorld } from '@/composables/useSandboxWorld'
import { aiProviderList, aiProviderModels } from '@/api/ai'
import { emojiForItem, rarityMeta, ITEM_RARITIES } from '@/utils/sandboxItems'
import {
  deleteSandboxQuest,
  expireSandboxQuest,
  generateSandboxQuests,
  resetSandboxQuest,
  sandboxLocations,
  sandboxQuests,
  sandboxSettings,
  sandboxWorlds,
  saveSandboxQuest,
  saveSandboxQuestSettings,
  setSandboxQuestProgress
} from '@/api/sandbox'

/** 委托类型：与后端 QUEST_TYPES 一一对应 */
const QUEST_TYPES = [
  { value: 'hunt', label: '讨伐', emoji: '⚔️' },
  { value: 'gather', label: '采集', emoji: '🌿' },
  { value: 'escort', label: '护送', emoji: '🛡️' },
  { value: 'explore', label: '探索', emoji: '🧭' },
  { value: 'chore', label: '杂活', emoji: '🧺' },
  { value: 'other', label: '其它', emoji: '📜' }
]

const worlds = ref([])
const { currentWorldId, setCurrentWorld } = useSandboxWorld()
const selectedWorldId = ref(null)
const locations = ref([])
const quests = ref([])
const loading = ref(false)
const generating = ref(false)
const saving = ref(false)
const savingSetting = ref(false)
const savingProgress = ref(false)
const statusFilter = ref('all')
/** 列表范围：board = 只看当前板面（和前台一致）；all = 连旧批次与已下架的历史一起看 */
const scopeFilter = ref('board')
const providers = ref([])
const models = ref([])
const modelLoading = ref(false)
const dialogVisible = ref(false)
const progressVisible = ref(false)

const settings = reactive({
  questTitle: '旅人委托板',
  questEnabled: '1',
  questPerGenerate: '4',
  questVisibleCount: '8',
  questProgressStepMax: '40',
  questStepMaxByDifficulty: '100,70,50,35,20',
  questProviderId: '',
  questModel: '',
  questPromptExtra: '',
  questSelfcheck: '1',
  questAutoEnabled: '1',
  questIntervalHours: '24',
  questAutoTime: '09:00'
})

/** 表单默认值：新增/编辑前都先整体重置，避免上一个委托的值残留 */
const FORM_DEFAULTS = {
  id: null,
  title: '',
  questType: 'hunt',
  difficulty: 2,
  locationName: '',
  target: '',
  power: 0,
  rewardCoins: 10,
  description: '',
  pinned: 0,
  rewards: []
}

const form = reactive({ ...FORM_DEFAULTS, rewards: [] })

const progressForm = reactive({ id: null, title: '', progress: 0, note: '' })

const stats = computed(() => {
  const result = { open: 0, taken: 0, completed: 0, expired: 0 }
  for (const quest of quests.value) {
    if (result[quest.status] != null) {
      result[quest.status] += 1
    }
  }
  return result
})

const batchText = computed(() => {
  const first = quests.value[0]
  return first && first.batchTime ? first.batchTime : '（还没有委托）'
})

function typeMeta(type) {
  return QUEST_TYPES.find((t) => t.value === type) || QUEST_TYPES[5]
}

function typeText(type) {
  return typeMeta(type).label
}

function typeEmoji(type) {
  return typeMeta(type).emoji
}

function difficultyStars(difficulty) {
  const level = Math.max(1, Math.min(5, Number(difficulty) || 1))
  return '★'.repeat(level) + '☆'.repeat(5 - level)
}

function statusMeta(status) {
  if (status === 'taken') {
    return { text: '接取中', type: 'warning' }
  }
  if (status === 'completed') {
    return { text: '已完成', type: 'success' }
  }
  if (status === 'expired') {
    return { text: '已下架', type: 'info' }
  }
  return { text: '可接', type: 'primary' }
}

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

async function load() {
  loading.value = true
  try {
    quests.value = (await sandboxQuests({
      worldId: selectedWorldId.value || undefined,
      status: statusFilter.value,
      scope: scopeFilter.value
    })) || []
    locations.value = (await sandboxLocations(selectedWorldId.value)) || []
    const s = await sandboxSettings()
    for (const key of Object.keys(settings)) {
      if (s && s[key] != null) {
        settings[key] = s[key]
      }
    }
    if (settings.questProviderId) {
      models.value = (await aiProviderModels(Number(settings.questProviderId))) || []
    }
  } finally {
    loading.value = false
  }
}

async function onSwitchWorld(id) {
  setCurrentWorld(id)
  await load()
}

/** 切回「当前板面」时把「已下架」筛掉（那个状态只在历史里才有） */
async function onSwitchScope() {
  if (scopeFilter.value === 'board' && statusFilter.value === 'expired') {
    statusFilter.value = 'all'
  }
  await load()
}

async function loadModels() {
  if (!settings.questProviderId) {
    ElMessage.warning('请先选择生成用的服务商（留空表示用系统服务商）')
    return
  }
  modelLoading.value = true
  try {
    models.value = (await aiProviderModels(Number(settings.questProviderId))) || []
    if (!models.value.length) {
      ElMessage.warning('没有获取到模型，可手动输入模型名')
    } else {
      ElMessage.success(`已获取 ${models.value.length} 个模型`)
    }
  } finally {
    modelLoading.value = false
  }
}

async function onGenerate() {
  generating.value = true
  try {
    const count = await generateSandboxQuests({
      worldId: selectedWorldId.value || undefined,
      providerId: settings.questProviderId || undefined,
      model: settings.questModel || undefined
    })
    if (count > 0) {
      ElMessage.success(`已生成 ${count} 条委托`)
    } else {
      ElMessage.info('接取中的委托已经占满，本轮没有生成新的（可以调大「每次生成条数」）')
    }
    await load()
  } finally {
    generating.value = false
  }
}

function openAdd() {
  Object.assign(form, FORM_DEFAULTS)
  form.rewards = []
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, FORM_DEFAULTS)
  form.id = row.id
  form.title = row.title || ''
  form.questType = row.questType || 'other'
  form.difficulty = row.difficulty || 1
  form.locationName = row.locationName || ''
  form.target = row.target || ''
  form.power = row.power == null ? 0 : row.power
  form.rewardCoins = row.rewardCoins == null ? 0 : row.rewardCoins
  form.description = row.description || ''
  form.pinned = row.pinned == null ? 0 : row.pinned
  // 奖励物品：深拷贝，避免直接改到表格里的数据
  form.rewards = (row.rewards || []).map((r) => ({
    name: r.name || '',
    rarity: r.rarity || 1,
    quantity: r.quantity || 1,
    description: r.description || ''
  }))
  dialogVisible.value = true
}

async function onSave() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写委托标题')
    return
  }
  saving.value = true
  try {
    await saveSandboxQuest({
      id: form.id,
      worldId: form.id ? undefined : selectedWorldId.value,
      title: form.title,
      questType: form.questType,
      difficulty: form.difficulty,
      locationName: form.locationName,
      target: form.target,
      // 0 表示"没有对手战力"，后端按 null 处理更干净
      power: form.power || null,
      rewardCoins: form.rewardCoins,
      description: form.description,
      pinned: form.pinned,
      rewards: form.rewards.filter((r) => (r.name || '').trim())
    })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

/** 置顶开关：不传 enabled，让后端保持原值（列表里已经没有「上板」开关了） */
async function onTogglePinned(row, value) {
  await saveSandboxQuest({
    id: row.id,
    title: row.title,
    questType: row.questType,
    difficulty: row.difficulty,
    locationName: row.locationName,
    target: row.target,
    power: row.power,
    rewardCoins: row.rewardCoins,
    description: row.description,
    rewards: (row.rewards || []).map((r) => ({ ...r })),
    pinned: value ? 1 : 0
  })
  row.pinned = value ? 1 : 0
}

function openProgress(row) {
  progressForm.id = row.id
  progressForm.title = row.title
  progressForm.progress = row.progress || 0
  progressForm.note = row.progressNote || ''
  progressVisible.value = true
}

async function onSaveProgress() {
  savingProgress.value = true
  try {
    await setSandboxQuestProgress(progressForm.id, progressForm.progress, progressForm.note)
    ElMessage.success('已更新进度')
    progressVisible.value = false
    await load()
  } finally {
    savingProgress.value = false
  }
}

async function onReset(row) {
  try {
    await ElMessageBox.confirm(
        `确定把「${row.title}」放回委托板吗？会回到「可接」并加入当前这一批（不会把其他委托挤下板）。` +
        `接取人清空、进度清零；已经发过的奖励不会回收，所以它也可能被再次完成并再发一次奖励。`,
      '重新上板',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  await resetSandboxQuest(row.id)
  ElMessage.success('已放回委托板')
  await load()
}

async function onExpire(row) {
  const taken = row.status === 'taken'
  try {
    await ElMessageBox.confirm(
      taken
        ? `「${row.title}」正被「${row.takerName || '某个角色'}」执行。撤下会立刻解除接取关系（进度清零、TA 下一步会重新看到委托列表），` +
          `但不会发放任何奖励。确定吗？`
        : `确定把「${row.title}」从委托板上撤下来吗？撤下后前台看不到、角色也不会再接它；` +
          `想再放回来点「重新上板」即可，被撤下的委托会在 3 天后由数据清理删除。`,
      '下架委托',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  await expireSandboxQuest(row.id)
  ElMessage.success('已下架')
  await load()
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除委托「${row.title}」吗？`, '删除委托', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteSandboxQuest(row.id)
  ElMessage.success('已删除')
  await load()
}

async function onSaveSettings() {
  savingSetting.value = true
  try {
    // 用委托专用接口：普通管理员只会写委托相关配置，改不到世界运行参数
    await saveSandboxQuestSettings({ ...settings })
    ElMessage.success('设置已保存')
    await load()
  } finally {
    savingSetting.value = false
  }
}

onMounted(async () => {
  providers.value = (await aiProviderList()) || []
  await initWorld()
  await load()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.tip { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.range-sep { margin: 0 8px; color: var(--el-text-color-secondary); }
.tips { margin-bottom: 14px; }
.stat-row { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 10px; }
.filter-row { margin-bottom: 4px; display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.filter-label { font-size: 12px; color: var(--el-text-color-secondary); }
.mt { margin-top: 14px; }
.quest-cell { display: flex; align-items: flex-start; gap: 10px; }
.quest-emoji { font-size: 20px; line-height: 1.2; }
.quest-title { font-weight: 600; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.quest-sub { font-size: 12px; color: var(--el-text-color-secondary); }
.quest-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.muted { color: var(--el-text-color-secondary); }
.board-tag { margin-left: 6px; }
.setting-form { max-width: 780px; }
/* 奖励物品编辑器：一行放不下就换行，绝不撑破弹窗（每件物品占两行） */
.reward-editor { width: 100%; display: flex; flex-direction: column; gap: 8px; align-items: flex-start; }
.reward-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}
/* 允许换行：即使弹窗被拖得很窄，也只会换行，不会把输入框挤出容器 */
.reward-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; width: 100%; }
.rw-name { flex: 1 1 auto; min-width: 0; }
.rw-del { flex: 0 0 auto; }
.rw-label { flex: 0 0 auto; font-size: 12px; color: var(--el-text-color-secondary); }
.rw-rarity { flex: 0 0 120px; width: 120px; }
.rw-qty { flex: 0 0 110px; width: 110px; }
.rw-desc { width: 100%; }
.rarity-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}
/* 表格里的报酬：金币胶囊 + 带品质配色的物品徽章（和集市一致） */
.reward-chips { display: flex; flex-wrap: wrap; gap: 4px; }
.rw-coin {
  font-size: 12px;
  font-weight: 700;
  color: #c98a2a;
  background: rgba(240, 177, 60, 0.16);
  border: 1px solid rgba(240, 177, 60, 0.45);
  border-radius: 999px;
  padding: 0 8px;
}
.rw-item {
  font-size: 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  padding: 0 8px;
  white-space: nowrap;
}
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
</style>
