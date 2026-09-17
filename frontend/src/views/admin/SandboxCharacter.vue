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
          <el-button plain :loading="repairing" @click="onRepairCoins">金币对账</el-button>
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
      <el-table-column label="当前目标" min-width="140">
        <template #default="{ row }">
          <span class="goal-cell">{{ row.goal || '—' }}</span>
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
          <!-- 行动卡住（AI 超时/进程重启）时手动解围；正常执行完锁会自动释放 -->
          <el-button v-if="row.runningAt" size="small" type="warning" plain @click="onUnlock(row)">
            解除执行锁
          </el-button>
          <!-- 执行完成后留一个时间戳：扫一眼就知道哪个角色这次跑过、哪个没跑 -->
          <div v-if="lastRunAt[row.id]" class="run-stamp">最近执行 · {{ lastRunAt[row.id] }}</div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 最近执行结果：单角色执行与「全员行动一轮」都写到这里，避免只看得到一闪而过的提示 -->
    <el-card class="mt run-results" shadow="never">
      <template #header>
        <div class="toolbar">
          <span>
            最近执行结果
            <span class="tip">（保留最近 {{ RUN_RESULT_LIMIT }} 条，刷新页面后清空）</span>
          </span>
          <div class="toolbar-right">
            <el-button size="small" @click="resultsCollapsed = !resultsCollapsed">
              {{ resultsCollapsed ? '展开' : '收起' }}
            </el-button>
            <el-button size="small" :disabled="!runResults.length" @click="runResults = []">清空</el-button>
          </div>
        </div>
      </template>
      <div v-show="!resultsCollapsed">
        <div v-if="!runResults.length" class="tip">
          还没有执行记录：点表格里的「立即执行」或右上角「全员行动一轮」，结果会显示在这里。
        </div>
        <div v-for="item in runResults" :key="item.id" class="run-item" :class="{ failed: !item.ok }">
          <div class="run-head">
            <el-tag size="small" :type="item.ok ? 'success' : 'danger'">{{ item.ok ? '成功' : '失败' }}</el-tag>
            <strong>{{ item.characterName }}</strong>
            <span class="tip">{{ item.time }}</span>
            <span v-if="item.ok && item.act && item.act.locationName" class="tip">
              在 {{ placeText(item.act) }}
            </span>
            <el-button size="small" text type="primary" @click="viewInActLog(item)">在行动日志里查看</el-button>
          </div>
          <template v-if="item.ok && item.act">
            <div class="run-text">{{ item.act.actions }}</div>
            <div v-if="item.act.innerVoice" class="run-voice">「{{ item.act.innerVoice }}」</div>
            <div class="run-tags">
              <span v-if="item.act.moveKm" class="run-tag">移动 {{ formatKm(item.act.moveKm) }}</span>
              <span v-if="item.act.coinChange" class="run-tag">
                金币 {{ item.act.coinChange > 0 ? '+' : '' }}{{ item.act.coinChange }}
              </span>
              <span v-if="item.act.combatChange" class="run-tag">
                战斗力 {{ item.act.combatChange > 0 ? '+' : '' }}{{ item.act.combatChange }}
              </span>
              <span v-if="item.act.itemChange" class="run-tag">{{ item.act.itemChange }}</span>
              <span v-if="item.act.companions" class="run-tag">与 {{ item.act.companions }} 互动</span>
              <span v-if="item.act.favorChange" class="run-tag">好感 {{ item.act.favorChange }}</span>
              <span v-if="item.act.nextAfterReason" class="run-tag">
                下次：{{ item.act.nextAfterReason }}（{{ item.act.nextAfterMinutes || 0 }} 分钟后）
              </span>
            </div>
          </template>
          <div v-else class="run-error">{{ item.error || '执行失败' }}</div>
        </div>
      </div>
    </el-card>

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
        <el-form-item label="当前目标">
          <el-input
            v-model="form.goal"
            maxlength="100"
            style="width: 320px"
            placeholder="例如：去晨雾森林采药（AI 会自己维护，你也可以直接改）"
          />
          <span class="tip">目标不同的角色会各自行动，不容易一直黏在一起</span>
        </el-form-item>
        <el-form-item label="对实力的看法">
          <el-input
            v-model="form.powerView"
            maxlength="60"
            style="width: 320px"
            placeholder="例如：不甘平庸，想变强"
          />
          <span class="tip">写进行动提示词：想变强的角色会主动修炼、拜师、攒钱买装备</span>
        </el-form-item>
        <el-form-item label="对财富的看法">
          <el-input
            v-model="form.wealthView"
            maxlength="60"
            style="width: 320px"
            placeholder="例如：穷怕了，拼命攒钱 / 钱是身外之物"
          />
          <span class="tip">写进行动提示词：看重钱的角色会多接委托、摆摊做买卖，不在意的就散财、安稳过日子</span>
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
import { useRouter } from 'vue-router'
import {
  sandboxCharacters,
  sandboxWorlds,
  saveSandboxCharacter,
  deleteSandboxCharacter,
  runSandboxCharacter,
  runAllSandboxCharacters,
  sandboxActs,
  sandboxItems,
  repairSandboxCoins,
  unlockSandboxCharacter,
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
/** 金币对账中 */
const repairing = ref(false)
/**
 * 「最近执行结果」面板：单角色执行与全员行动一轮的结果都写这里，最新在最上面。
 * 只存在内存里（刷新即清空），用来弥补"执行完成只有一个一闪而过的提示"。
 */
const runResults = ref([])
const resultsCollapsed = ref(false)
const RUN_RESULT_LIMIT = 8
/** 每个角色最近一次执行的完成时间（行内展示「最近执行 · 12:34」） */
const lastRunAt = reactive({})
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
const router = useRouter()
const selectedWorldId = ref(null)
const temperature = ref(0.9)
/** 标准状态项，对应 AI 提示词里的固定字段 */
const STANDARD_STATUS_KEYS = ['体力', '魔力', '饥饿度', '心情']
const statusForm = reactive({ 体力: 100, 魔力: 100, 饥饿度: 20, 心情: '平静' })
const extraStatus = ref([])

/**
 * 表单默认值：新增/编辑前都先整体重置。
 * 教训：以前编辑是"手写字段清单"塞进表单，漏了「战斗力 / 当前目标」，
 * 结果编辑 A 保存后，B 的战斗力被写成了 A 的值（数据被改坏）。
 * 现在改成"先重置 → 再按字段名批量灌入这一行的值"，以后新增字段也不会再漏。
 */
const FORM_DEFAULTS = {
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
  goal: '',
  powerView: '',
  wealthView: '',
  intervalMin: 45,
  intervalMax: 75,
  aiIntervalMin: null,
  aiIntervalMax: null,
  enabled: 1
}

const form = reactive({ ...FORM_DEFAULTS })

/** 把表单整体恢复成默认值（含状态项、草稿物品等附件） */
function resetForm() {
  Object.assign(form, FORM_DEFAULTS)
  statusForm.体力 = 100
  statusForm.魔力 = 100
  statusForm.饥饿度 = 20
  statusForm.心情 = '平静'
  extraStatus.value = []
  draftItems.value = []
  draftPlace.value = ''
  models.value = []
  temperature.value = 0.9
}

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
  // 换了世界，执行结果与行内时间戳都属于上一个世界，清掉避免张冠李戴
  runResults.value = []
  Object.keys(lastRunAt).forEach((key) => delete lastRunAt[key])
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
  resetForm()
  // 新建时的默认服务商：沿用列表里第一个（AI 一键创作也用它）
  form.providerId = providers.value.length ? providers.value[0].id : null
  aiProviderId.value = form.providerId || (providers.value.length ? providers.value[0].id : null)
  aiModel.value = ''
  aiModels.value = []
  aiRequirement.value = ''
  generateFirstAct.value = true
  dialogVisible.value = true
}

function openEdit(row) {
  // 先整体重置，再按字段名批量灌入这一行的值：
  // 这样"表单里有、但这行没给"的字段会回到默认值，而不会残留上一个角色的值
  resetForm()
  Object.keys(form).forEach((key) => {
    if (key !== 'id' && row[key] !== undefined) {
      form[key] = row[key]
    }
  })
  form.id = row.id
  // 几个需要归一化的字段（空值要回落到默认，而不是把 null 提交上去）
  form.name = row.name || ''
  form.title = row.title || ''
  form.avatar = row.avatar || ''
  form.appearance = row.appearance || ''
  form.persona = row.persona || ''
  form.model = row.model || ''
  form.temperature = Number(row.temperature || 0.9)
  form.x = row.x == null ? 50 : row.x
  form.y = row.y == null ? 50 : row.y
  form.coins = row.coins == null ? 0 : row.coins
  form.combatPower = row.combatPower == null ? 10 : row.combatPower
  form.goal = row.goal || ''
  form.powerView = row.powerView || ''
  form.wealthView = row.wealthView || ''
  form.intervalMin = row.intervalMin || 45
  form.intervalMax = row.intervalMax || 75
  form.aiIntervalMin = row.aiIntervalMin == null ? null : row.aiIntervalMin
  form.aiIntervalMax = row.aiIntervalMax == null ? null : row.aiIntervalMax
  form.enabled = row.enabled == null ? 1 : row.enabled
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
  // 对实力/财富的态度：AI 生成时一并填充，管理员可以改
  form.powerView = draft.powerView || form.powerView
  form.wealthView = draft.wealthView || form.wealthView
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
          // 第一条行动的结果也写进「最近执行结果」，避免只弹一个一闪而过的提示
          const fresh = await fetchNewestAct(saved.id)
          pushRunResult({
            characterId: saved.id,
            characterName: saved.name,
            ok: true,
            act: fresh || act
          })
          lastRunAt[saved.id] = timeText(fresh ? fresh.createTime : act.createTime)
        } catch (e) {
          ElMessage.warning('角色已创建，但第一条行动生成失败，可在列表里点「立即执行」重试')
          pushRunResult({
            characterId: saved.id,
            characterName: saved.name,
            ok: false,
            error: (e && e.message) || '第一条行动生成失败'
          })
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
    // 再取一次这条记录：列表接口会补上「移动 N km」这类服务端算好的字段，展示更完整
    const fresh = await fetchNewestAct(row.id)
    pushRunResult({
      characterId: row.id,
      characterName: row.name,
      ok: true,
      act: fresh || act
    })
    lastRunAt[row.id] = timeText(fresh ? fresh.createTime : act.createTime)
    ElMessage.success(`「${row.name}」执行完成，结果见下方「最近执行结果」`)
    await load()
  } catch (e) {
    // 失败也留一条记录，避免只看得到一闪而过的报错提示
    pushRunResult({
      characterId: row.id,
      characterName: row.name,
      ok: false,
      error: (e && e.message) || '执行失败'
    })
    lastRunAt[row.id] = nowTimeText()
  } finally {
    runningIds.value = runningIds.value.filter((id) => id !== row.id)
  }
}

/** 某个角色的「立即执行」是否正在运行 */
function isRunning(id) {
  return runningIds.value.includes(id)
}

// ============================== 最近执行结果 ==============================

/** 往结果面板顶部插入一条（超过上限就丢掉最旧的） */
function pushRunResult(entry) {
  runResults.value.unshift({
    id: Date.now() + Math.round(Math.random() * 1000),
    time: nowTimeText(),
    ...entry
  })
  if (runResults.value.length > RUN_RESULT_LIMIT) {
    runResults.value = runResults.value.slice(0, RUN_RESULT_LIMIT)
  }
}

/** 取某角色最新一条行动：列表接口会补上「移动 N km」，比直接用执行返回值更完整 */
async function fetchNewestAct(characterId) {
  try {
    const data = await sandboxActs({ characterId, page: 1, size: 1 })
    return (data.list || [])[0] || null
  } catch (e) {
    return null
  }
}

/** 「2026-09-17 12:34:56」→「12:34」；拿不到时间就退回当前时间 */
function timeText(value) {
  if (!value) {
    return nowTimeText()
  }
  const text = String(value)
  const index = text.indexOf(' ')
  return index > 0 ? text.slice(index + 1, index + 6) : text.slice(0, 5)
}

function nowTimeText() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

/** 移动距离文案（与前台一致：10 km 内保留一位小数） */
function formatKm(km) {
  const value = Number(km) || 0
  return value < 10 ? value.toFixed(1) + ' km' : Math.round(value) + ' km'
}

/** 结果里的地点文案：一级地点 + 二级地点 */
function placeText(act) {
  if (!act || !act.locationName) {
    return ''
  }
  return act.subLocation ? `${act.locationName} · ${act.subLocation}` : act.locationName
}

/** 跳到「行动日志」并按该角色筛选，方便看这条行动的完整上下文 */
function viewInActLog(item) {
  router.push({ path: '/admin/sandbox/acts', query: { characterId: item.characterId } })
}

/** 金币对账：按流水重算余额（历史上"整值回写"造成的丢失更新靠这个修复） */
async function onRepairCoins() {
  try {
    await ElMessageBox.confirm(
      '将按金币流水重算每个角色的余额，并把流水里的「当时余额」也重算一遍。只修数据、不删数据，确定继续吗？',
      '金币对账',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  repairing.value = true
  try {
    const report = await repairSandboxCoins(selectedWorldId.value)
    const details = report.details || []
    if (!details.length) {
      ElMessage.success(`检查了 ${report.checked || 0} 个角色，金币都没问题`)
      return
    }
    const lines = details.map((row) => `${row.characterName}：${row.before} → ${row.after}${row.logsFixed ? `（流水修正 ${row.logsFixed} 条）` : ''}`)
    await ElMessageBox.alert(
      `修正了 ${report.fixed || 0} 个角色的余额：<br/><br/>${lines.map((line) => `· ${line}`).join('<br/>')}`,
      '对账结果',
      { dangerouslyUseHTMLString: true }
    ).catch(() => {})
    await load()
  } finally {
    repairing.value = false
  }
}

/** 解除某个角色的执行锁（行动卡住时的应急出口） */
async function onUnlock(row) {
  try {
    await ElMessageBox.confirm(
      '解除执行锁只会清掉"正在执行"这个标记，不会中断服务器上可能还在跑的那次调用。'
        + '仅在角色一直显示正在执行、且你已经确认没有请求在跑时使用。确定吗？',
      '解除执行锁',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  await unlockSandboxCharacter(row.id)
  ElMessage.success(`已解除「${row.name}」的执行锁`)
  await load()
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
    // 结果逐条写进「最近执行结果」面板：成功的取回那条行动（含移动距离），失败的记下原因
    for (const character of list.value) {
      const line = (res.items || []).find((text) => text.startsWith(character.name + '：'))
      if (!line) {
        continue
      }
      const text = line.slice(character.name.length + 1)
      if (text.startsWith('失败')) {
        pushRunResult({
          characterId: character.id,
          characterName: character.name,
          ok: false,
          error: text.replace(/^失败（/, '').replace(/）$/, '')
        })
        lastRunAt[character.id] = nowTimeText()
        continue
      }
      const act = await fetchNewestAct(character.id)
      pushRunResult({
        characterId: character.id,
        characterName: character.name,
        ok: true,
        act
      })
      if (act) {
        lastRunAt[character.id] = timeText(act.createTime)
      }
    }
    ElMessage.success(`本轮完成：成功 ${res.success} 个，失败 ${res.failed} 个，详见下方「最近执行结果」`)
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
/* 最近执行结果面板 */
.run-results { margin-top: 16px; }
.run-stamp { margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; }
.run-item {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  margin-bottom: 10px;
  background: var(--el-fill-color-blank);
}
.run-item.failed { border-color: var(--el-color-danger-light-5); background: var(--el-color-danger-light-9); }
.run-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.run-head .tip { margin-left: 0; }
.run-text { margin-top: 6px; white-space: pre-line; line-height: 1.7; font-size: 13px; }
.run-voice { margin-top: 4px; color: #a4638a; font-size: 13px; font-style: italic; }
.run-tags { margin-top: 6px; display: flex; gap: 8px; flex-wrap: wrap; }
.run-tag {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}
.run-error { margin-top: 6px; color: var(--el-color-danger); font-size: 13px; }
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
.goal-cell { color: #4f9d8f; }
</style>
