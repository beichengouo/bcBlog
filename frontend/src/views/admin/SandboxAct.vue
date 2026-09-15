<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>沙盒日志</span>
        <div class="toolbar-right">
          <el-select v-model="characterId" clearable placeholder="全部角色" style="width: 180px" @change="reload">
            <el-option v-for="c in characters" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-button @click="reload">刷新</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="tab" @tab-change="reload">
      <el-tab-pane label="行动日志" name="acts">
        <el-table :data="acts" v-loading="loadingActs">
          <el-table-column prop="createTime" label="时间" width="165" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }">{{ characterName(row.characterId) }}</template>
          </el-table-column>
          <el-table-column label="地点" width="150">
            <template #default="{ row }">
              <div>{{ row.locationName || '—' }}</div>
              <div class="muted">x={{ row.x }} , y={{ row.y }}</div>
            </template>
          </el-table-column>
          <el-table-column label="做了什么" min-width="240">
            <template #default="{ row }">
              <div class="multiline">{{ row.actions || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="心声" min-width="200">
            <template #default="{ row }">
              <div class="multiline">{{ row.innerVoice || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="概括" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.summary || '—' }}</template>
          </el-table-column>
          <el-table-column label="互动" width="140">
            <template #default="{ row }">
              <el-tag v-if="row.companions" size="small" type="warning">与 {{ row.companions }} 互动</el-tag>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.fromAi === 1 ? 'success' : 'warning'">
                {{ row.fromAi === 1 ? (row.manual === 1 ? '手动执行' : 'AI 自动') : '解析失败' }}
              </el-tag>
              <el-tag v-if="row.reaction === 1" size="small" type="warning" class="react-tag">回应</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="onDeleteAct(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pager"
          layout="total, prev, pager, next"
          :total="actTotal"
          :page-size="pageSize"
          :current-page="actPage"
          @current-change="onActPageChange"
        />
      </el-tab-pane>

      <el-tab-pane label="旅人低语" name="whispers">
        <el-table :data="whispers" v-loading="loadingWhispers">
          <el-table-column prop="createTime" label="时间" width="165" />
          <el-table-column label="用户" width="160">
            <template #default="{ row }">
              <div class="user-cell">
                <img v-if="row.userAvatar" class="user-avatar" :src="row.userAvatar" alt="" />
                <span>{{ row.userName || ('用户#' + row.userId) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="角色" width="120">
            <template #default="{ row }">{{ characterName(row.characterId) }}</template>
          </el-table-column>
          <el-table-column prop="content" label="低语内容" min-width="300" show-overflow-tooltip />
          <el-table-column label="消耗积分" width="100">
            <template #default="{ row }">{{ row.pointsCost }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="onDeleteWhisper(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pager"
          layout="total, prev, pager, next"
          :total="whisperTotal"
          :page-size="pageSize"
          :current-page="whisperPage"
          @current-change="onWhisperPageChange"
        />
      </el-tab-pane>

      <el-tab-pane label="金币流水" name="coins">
        <el-table :data="coinLogs" v-loading="loadingCoins">
          <el-table-column prop="createTime" label="时间" width="165" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }">{{ characterName(row.characterId) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="coinTagType(row.type)">{{ coinTypeText(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金币变化" width="100">
            <template #default="{ row }">
              <span :class="row.coins > 0 ? 'plus' : 'minus'">{{ row.coins > 0 ? '+' : '' }}{{ row.coins }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="balance" label="变化后余额" width="110" />
          <el-table-column label="来源" width="150">
            <template #default="{ row }">{{ row.userName || '系统' }}</template>
          </el-table-column>
          <el-table-column label="消耗积分" width="100">
            <template #default="{ row }">{{ row.pointsCost || 0 }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="onDeleteCoinLog(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pager"
          layout="total, prev, pager, next"
          :total="coinTotal"
          :page-size="pageSize"
          :current-page="coinPage"
          @current-change="onCoinPageChange"
        />
      </el-tab-pane>

      <el-tab-pane label="角色关系" name="relations">
        <div class="tab-tools">
          <el-button type="primary" size="small" @click="openRelationEdit(null)">新增关系</el-button>
          <span class="muted">
            好感度 -100 ~ 100：AI 会根据角色互动自动加减，这里也可以手动修正；前台角色档案会展示双方对彼此的好感度
          </span>
        </div>
        <el-table :data="relations" v-loading="loadingRelations">
          <el-table-column label="角色" width="140">
            <template #default="{ row }">{{ row.characterName || ('角色#' + row.characterId) }}</template>
          </el-table-column>
          <el-table-column label="对谁" width="140">
            <template #default="{ row }">{{ row.targetName || ('角色#' + row.targetId) }}</template>
          </el-table-column>
          <el-table-column label="好感度" min-width="220">
            <template #default="{ row }">
              <div class="favor-cell">
                <el-progress
                  :percentage="favorPercent(row.favor)"
                  :stroke-width="8"
                  :show-text="false"
                  :color="favorColor(row.favor)"
                />
                <span class="favor-value">{{ row.favor }} · {{ row.favorLevel }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="对方对我" width="140">
            <template #default="{ row }">{{ row.reverseFavor }} · {{ row.reverseFavorLevel }}</template>
          </el-table-column>
          <el-table-column label="最近变化" width="110">
            <template #default="{ row }">
              <span v-if="row.lastChange" :class="row.lastChange > 0 ? 'plus' : 'minus'">
                {{ row.lastChange > 0 ? '+' : '' }}{{ row.lastChange }}
              </span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column prop="updateTime" label="更新时间" width="165" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="openRelationEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="onDeleteRelation(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="relationVisible" :title="relationForm.id ? '编辑关系' : '新增关系'" width="min(92vw, 460px)">
      <el-form :model="relationForm" label-width="90px">
        <el-form-item label="角色">
          <el-select v-model="relationForm.characterId" placeholder="选择角色" style="width: 100%">
            <el-option v-for="c in characters" :key="'rc-' + c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对谁">
          <el-select v-model="relationForm.targetId" placeholder="选择对象" style="width: 100%">
            <el-option
              v-for="c in characters"
              :key="'rt-' + c.id"
              :label="c.name"
              :value="c.id"
              :disabled="c.id === relationForm.characterId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="好感度">
          <el-slider v-model="relationForm.favor" :min="-100" :max="100" :step="1" show-input />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="relationForm.remark" maxlength="200" placeholder="例如：在集市认识、一起做过委托" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRelation" @click="onSaveRelation">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  sandboxCharacters,
  sandboxActs,
  deleteSandboxAct,
  sandboxInteractions,
  deleteSandboxInteraction,
  sandboxCoinLogs,
  deleteSandboxCoinLog,
  sandboxRelations,
  saveSandboxRelation,
  deleteSandboxRelation
} from '@/api/sandbox'

const tab = ref('acts')
const characters = ref([])
const characterId = ref(null)

const acts = ref([])
const actTotal = ref(0)
const actPage = ref(1)
const loadingActs = ref(false)

const whispers = ref([])
const whisperTotal = ref(0)
const whisperPage = ref(1)
const loadingWhispers = ref(false)

const coinLogs = ref([])
const coinTotal = ref(0)
const coinPage = ref(1)
const loadingCoins = ref(false)

const relations = ref([])
const loadingRelations = ref(false)
const relationVisible = ref(false)
const savingRelation = ref(false)
const relationForm = reactive({ id: null, characterId: null, targetId: null, favor: 0, remark: '' })

const pageSize = 10

async function loadCharacters() {
  try {
    characters.value = await sandboxCharacters()
  } catch (e) {
    characters.value = []
  }
}

function characterName(id) {
  const hit = characters.value.find((c) => c.id === id)
  return hit ? hit.name : `角色#${id}`
}

async function loadActs() {
  loadingActs.value = true
  try {
    const data = await sandboxActs({ characterId: characterId.value || undefined, page: actPage.value, size: pageSize })
    acts.value = data.list || []
    actTotal.value = data.total || 0
  } finally {
    loadingActs.value = false
  }
}

async function loadWhispers() {
  loadingWhispers.value = true
  try {
    const data = await sandboxInteractions({
      characterId: characterId.value || undefined,
      page: whisperPage.value,
      size: pageSize
    })
    whispers.value = data.list || []
    whisperTotal.value = data.total || 0
  } finally {
    loadingWhispers.value = false
  }
}

function reload() {
  if (tab.value === 'acts') {
    loadActs()
  } else if (tab.value === 'whispers') {
    loadWhispers()
  } else if (tab.value === 'coins') {
    loadCoinLogs()
  } else {
    loadRelations()
  }
}

async function loadRelations() {
  loadingRelations.value = true
  try {
    relations.value = await sandboxRelations({ characterId: characterId.value || undefined })
  } finally {
    loadingRelations.value = false
  }
}

/** 好感度 -100~100 映射到 0~100 的进度条 */
function favorPercent(favor) {
  const value = Number(favor || 0)
  return Math.max(0, Math.min(100, Math.round((value + 100) / 2)))
}

function favorColor(favor) {
  const value = Number(favor || 0)
  if (value < 0) return '#e2664f'
  if (value < 40) return '#b9a5c9'
  if (value < 80) return '#7bc47f'
  return '#f2b23e'
}

function openRelationEdit(row) {
  if (row) {
    relationForm.id = row.id
    relationForm.characterId = row.characterId
    relationForm.targetId = row.targetId
    relationForm.favor = row.favor == null ? 0 : row.favor
    relationForm.remark = row.remark || ''
  } else {
    relationForm.id = null
    relationForm.characterId = characters.value.length ? characters.value[0].id : null
    relationForm.targetId = characters.value.length > 1 ? characters.value[1].id : null
    relationForm.favor = 0
    relationForm.remark = ''
  }
  relationVisible.value = true
}

async function onSaveRelation() {
  if (!relationForm.characterId || !relationForm.targetId) {
    ElMessage.warning('请选择角色与对象')
    return
  }
  if (relationForm.characterId === relationForm.targetId) {
    ElMessage.warning('角色不能和自己建立好感度')
    return
  }
  savingRelation.value = true
  try {
    await saveSandboxRelation({ ...relationForm })
    ElMessage.success('好感度已保存')
    relationVisible.value = false
    await loadRelations()
  } finally {
    savingRelation.value = false
  }
}

async function onDeleteRelation(row) {
  await ElMessageBox.confirm('确定删除这条好感度记录吗？', '提示', { type: 'warning' })
  await deleteSandboxRelation(row.id)
  ElMessage.success('已删除')
  await loadRelations()
}

async function loadCoinLogs() {
  loadingCoins.value = true
  try {
    const data = await sandboxCoinLogs({
      characterId: characterId.value || undefined,
      page: coinPage.value,
      size: pageSize
    })
    coinLogs.value = data.list || []
    coinTotal.value = data.total || 0
  } finally {
    loadingCoins.value = false
  }
}

function onCoinPageChange(page) {
  coinPage.value = page
  loadCoinLogs()
}

function coinTypeText(type) {
  if (type === 'contribute') return '旅人贡献'
  if (type === 'earn') return '日常赚取'
  if (type === 'spend') return '日常消耗'
  return '管理员调整'
}

function coinTagType(type) {
  if (type === 'contribute') return 'warning'
  if (type === 'earn') return 'success'
  if (type === 'spend') return 'info'
  return 'primary'
}

async function onDeleteCoinLog(row) {
  await ElMessageBox.confirm('确定删除这条金币流水吗？（只删记录，不改变角色金币余额）', '提示', { type: 'warning' })
  await deleteSandboxCoinLog(row.id)
  ElMessage.success('已删除')
  await loadCoinLogs()
}

function onActPageChange(page) {
  actPage.value = page
  loadActs()
}

function onWhisperPageChange(page) {
  whisperPage.value = page
  loadWhispers()
}

async function onDeleteAct(row) {
  await ElMessageBox.confirm('确定删除这条行动记录吗？', '提示', { type: 'warning' })
  await deleteSandboxAct(row.id)
  ElMessage.success('已删除')
  await loadActs()
}

async function onDeleteWhisper(row) {
  await ElMessageBox.confirm('确定删除这条旅人低语吗？', '提示', { type: 'warning' })
  await deleteSandboxInteraction(row.id)
  ElMessage.success('已删除')
  await loadWhispers()
}

onMounted(async () => {
  await loadCharacters()
  await loadActs()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.multiline { white-space: pre-line; line-height: 1.6; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.user-cell { display: flex; align-items: center; gap: 8px; }
.user-avatar { width: 26px; height: 26px; border-radius: 50%; object-fit: cover; }
.pager { margin-top: 14px; justify-content: flex-end; }
.plus { color: #3f9e4d; font-weight: 600; }
.minus { color: #d4553f; font-weight: 600; }
.tab-tools { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.react-tag { margin-left: 4px; }
.favor-cell :deep(.el-progress) { margin-bottom: 4px; }
.favor-value { font-size: 12px; color: var(--el-text-color-regular); }
</style>
