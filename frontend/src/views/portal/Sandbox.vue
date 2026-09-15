<template>
  <div class="sandbox">
    <header class="sandbox-head">
      <h1>{{ world.name || '沙盒世界' }}</h1>
      <p>{{ world.description || '管理员还没有填写世界简介' }}</p>
      <span v-if="!enabled" class="paused">AI 自动行动已暂停，当前只展示历史记录</span>
    </header>

    <section class="map-card">
      <div v-if="!world.mapImage" class="map-empty">
        <p>管理员还没有上传地图背景</p>
        <p class="sub">可以在后台「站点管理 → 沙盒世界」上传地图并添加地点</p>
      </div>
      <div v-else class="map-stage">
        <img class="map-bg" :src="world.mapImage" alt="地图" />
        <div
          v-for="loc in locations"
          :key="'loc-' + loc.id"
          class="loc"
          :style="{ left: loc.x + '%', top: loc.y + '%' }"
          :title="loc.description || loc.name"
        >
          <span class="loc-icon"><LocationIcon :icon="loc.icon" :size="16" /></span>
          <span class="loc-name">{{ loc.name }}</span>
        </div>
        <!-- 多个角色挤在同一个坐标时，标记会自动错开，这里在真实落点画一个锚点 -->
        <div
          v-for="anchor in anchors"
          :key="'anchor-' + anchor.x + '-' + anchor.y"
          class="anchor"
          :style="{ left: anchor.x + '%', top: anchor.y + '%' }"
        >
          <span class="anchor-dot"></span>
          <span class="anchor-count">{{ anchor.size }}</span>
        </div>
        <div
          v-for="c in displayCharacters"
          :key="'actor-' + c.id"
          class="actor"
          :class="{ active: activeId === c.id, stacked: c.groupSize > 1 }"
          :style="{ left: c.displayX + '%', top: c.displayY + '%' }"
          @click="selectCharacter(c)"
        >
          <img v-if="c.avatar" class="actor-avatar" :src="c.avatar" :alt="c.name" />
          <span v-else class="actor-fallback">{{ (c.name || '?').slice(0, 1) }}</span>
          <span class="actor-name">{{ c.name }}</span>
        </div>

        <button type="button" class="map-zoom-btn" @click="openMapViewer">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
          </svg>
          <span>放大查看</span>
        </button>
      </div>
    </section>

    <!-- 全屏地图：手机上看地图更方便 -->
    <SandboxMapViewer
      v-model:visible="mapViewerVisible"
      :map-image="world.mapImage"
      :title="world.name"
      :locations="locations"
      :characters="displayCharacters"
      :anchors="anchors"
      :active-id="activeId"
      @select="onViewerSelect"
    />

    <section class="card panel">
      <div class="panel-head">
        <h3>角色档案</h3>
        <div class="panel-tools">
          <input v-model="keyword" class="char-search" type="search" placeholder="搜索角色名 / 称号 / 所在地" />
          <button class="ghost-btn" @click="collapsed = !collapsed">
            {{ collapsed ? '展开' : '收起' }}
          </button>
        </div>
      </div>

      <div v-show="!collapsed" class="panel-body">
        <div class="char-chips">
          <button
            v-for="c in filteredCharacters"
            :key="'chip-' + c.id"
            class="char-chip"
            :class="{ on: activeId === c.id }"
            @click="selectCharacter(c)"
          >
            <img v-if="c.avatar" class="chip-avatar" :src="c.avatar" :alt="c.name" />
            <span v-else class="chip-avatar fallback">{{ (c.name || '?').slice(0, 1) }}</span>
            <span class="chip-text">
              <span class="chip-name">{{ c.name }}</span>
              <span class="chip-loc">{{ c.locationName || '尚未行动' }}</span>
            </span>
          </button>
          <div v-if="!filteredCharacters.length" class="muted">
            {{ characters.length ? '没有匹配的角色' : '管理员还没有添加角色' }}
          </div>
        </div>

        <div v-if="active" class="detail">
          <div class="detail-head">
            <img v-if="active.avatar" class="detail-avatar" :src="active.avatar" :alt="active.name" />
            <div class="detail-title">
              <h2>{{ active.name }}</h2>
              <p class="title">{{ active.title || '旅行者' }}</p>
              <p class="muted">当前位置：{{ active.locationName || '尚未行动' }}</p>
              <p class="muted">下次行动：{{ nextRunText(active) }}</p>
            </div>
            <div class="status">
              <span class="chip coin-chip">金币 {{ active.coins || 0 }}</span>
              <span v-for="(value, key) in active.status || {}" :key="key" class="chip">
                {{ key }} {{ value }}
              </span>
            </div>
          </div>

          <p v-if="active.appearance" class="appearance">{{ active.appearance }}</p>

          <div class="contribute">
            <div class="contribute-head">
              <strong>为 TA 贡献金币</strong>
              <span class="muted">
                1 积分 = {{ coinRate }} 金币<template v-if="isLogin">（我的积分：{{ myPoints }}）</template>
              </span>
            </div>
            <div class="contribute-actions">
              <button
                v-for="p in [1, 5, 10]"
                :key="'preset-' + p"
                class="chip-btn"
                :class="{ on: contributePoints === p }"
                @click="contributePoints = p"
              >
                {{ p }} 积分
              </button>
              <input v-model.number="contributePoints" class="num-input" type="number" min="1" max="100" />
              <button class="send" :disabled="contributing" @click="onContribute">
                {{ contributing ? '贡献中…' : `贡献 ${contributePoints || 0} 积分 → ${(contributePoints || 0) * coinRate} 金币` }}
              </button>
            </div>
            <p class="muted">金币会用在角色的日常开销上（吃饭、住店、买材料），角色也会靠做工或接委托把它赚回来。</p>
          </div>

          <div v-if="(active.recentCoins || []).length" class="coin-history">
            <h3>金币记录</h3>
            <div v-for="log in active.recentCoins" :key="'coin-' + log.id" class="coin-item">
              <span class="coin-type">{{ coinTypeText(log.type) }}</span>
              <span class="coin-delta" :class="log.coins > 0 ? 'plus' : 'minus'">
                {{ log.coins > 0 ? '+' : '' }}{{ log.coins }}
              </span>
              <span class="coin-remark">{{ log.remark }}</span>
              <span class="time">{{ log.createTime }}</span>
            </div>
          </div>

          <div v-if="(active.relations || []).length" class="relations">
            <h3>与其他角色的关系</h3>
            <div v-for="rel in active.relations" :key="'rel-' + rel.targetId" class="relation-item">
              <img v-if="rel.targetAvatar" class="relation-avatar" :src="rel.targetAvatar" alt="" />
              <span v-else class="relation-avatar fallback">{{ (rel.targetName || '?').slice(0, 1) }}</span>
              <div class="relation-main">
                <div class="relation-name">
                  {{ rel.targetName }}
                  <span class="muted">
                    {{ rel.targetTitle || '旅行者' }} · {{ rel.targetLocation || '行踪不明' }}
                  </span>
                </div>
                <div class="relation-bar">
                  <div class="relation-track">
                    <div
                      class="relation-fill"
                      :style="{ width: favorPercent(rel.favor) + '%', background: favorColor(rel.favor) }"
                    ></div>
                  </div>
                  <span class="relation-value">好感 {{ rel.favor }} · {{ rel.favorLevel }}</span>
                  <span
                    v-if="rel.lastChange"
                    class="favor-delta"
                    :class="rel.lastChange > 0 ? 'plus' : 'minus'"
                  >
                    较上次 {{ rel.lastChange > 0 ? '+' : '' }}{{ rel.lastChange }}
                  </span>
                </div>
                <div class="relation-reverse">TA 对你的好感：{{ rel.reverseFavor }} · {{ rel.reverseFavorLevel }}</div>
                <div v-if="rel.remark" class="relation-remark">印象：{{ rel.remark }}</div>
              </div>
            </div>
          </div>

          <div class="recent">
            <h3>最近的行动</h3>
            <div v-if="!(active.recentActs || []).length" class="muted">还没有行动记录</div>
            <div v-for="act in active.recentActs || []" :key="act.id" class="act">
              <div class="act-meta">
                <span class="time">{{ act.createTime }}</span>
                <span class="place">{{ act.locationName || '某处' }}</span>
                <span v-if="act.companions" class="companion-tag">与 {{ act.companions }} 互动</span>
                <span v-if="act.favorChange" class="favor-tag">好感 {{ act.favorChange }}</span>
              </div>
              <div class="act-body">{{ act.actions }}</div>
              <div v-if="act.innerVoice" class="voice">「{{ act.innerVoice }}」</div>
            </div>
          </div>
        </div>
        <div v-else class="muted">请选择一个角色查看档案</div>
      </div>
    </section>

    <section class="card panel">
      <div class="panel-head">
        <h3>旅人低语</h3>
        <div class="panel-tools">
          <label class="target-label">低语对象</label>
          <el-select
            v-model="whisperTargetId"
            class="target-select"
            placeholder="选择要互动的角色"
            filterable
            @change="loadWhispers"
          >
            <el-option v-for="c in characters" :key="'t-' + c.id" :label="c.name" :value="c.id">
              <span class="option-name">{{ c.name }}</span>
              <span class="option-loc">{{ c.locationName || '尚未行动' }}</span>
            </el-option>
          </el-select>
        </div>
      </div>

      <div class="panel-body">
        <p class="muted">
          登录后可以给指定角色留下一句话，{{ whisperPoints }} 积分一次，对方下一次行动时可能会看见它。
        </p>
        <div class="whisper-list">
          <div v-if="!whispers.length" class="muted">还没有人给这位角色留下低语</div>
          <div v-for="item in whispers" :key="item.id" class="whisper-item">
            <img v-if="item.userAvatar" class="whisper-avatar" :src="item.userAvatar" alt="" />
            <span v-else class="whisper-avatar fallback">{{ (item.userName || '旅').slice(0, 1) }}</span>
            <div class="whisper-main">
              <div class="whisper-user">
                {{ item.userName || '旅人' }}
                <span class="time">{{ item.createTime }}</span>
              </div>
              <div class="whisper-text">{{ item.content }}</div>
            </div>
          </div>
        </div>
        <div class="whisper-form">
          <textarea
            v-model="whisperText"
            maxlength="200"
            :placeholder="isLogin ? `对「${whisperTargetName}」说点什么…` : '登录后即可留下低语'"
          />
          <button class="send" :disabled="sending" @click="sendWhisper">
            {{ sending ? '发送中…' : `送给「${whisperTargetName}」（${whisperPoints} 积分）` }}
          </button>
        </div>
        <p v-if="isLogin" class="muted">当前积分：{{ myPoints }}</p>
      </div>
    </section>

    <section class="card timeline">
      <div class="timeline-head">
        <h3>行动时间线</h3>
        <div class="filters">
          <button class="chip-btn" :class="{ on: !filterId }" @click="setFilter(null)">全部</button>
          <button
            v-for="c in characters"
            :key="'f-' + c.id"
            class="chip-btn"
            :class="{ on: filterId === c.id }"
            @click="setFilter(c.id)"
          >
            {{ c.name }}
          </button>
        </div>
      </div>
      <div v-if="!timeline.length" class="muted">还没有行动记录</div>
      <div v-for="act in timeline" :key="act.id" class="timeline-item">
        <div class="timeline-time">{{ act.createTime }}</div>
        <div class="timeline-content">
          <div class="timeline-title">
            <strong>{{ characterName(act.characterId) }}</strong>
            <span class="muted">在 {{ act.locationName || '某处' }}</span>
            <span v-if="act.coinChange" class="coin-delta inline" :class="act.coinChange > 0 ? 'plus' : 'minus'">
              {{ act.coinChange > 0 ? '+' : '' }}{{ act.coinChange }} 金币
            </span>
            <span v-if="act.companions" class="companion-tag inline">与 {{ act.companions }} 互动</span>
            <span v-if="act.favorChange" class="favor-tag inline">好感 {{ act.favorChange }}</span>
            <span v-if="act.reaction === 1" class="react-tag inline">回应</span>
          </div>
          <div class="act-body">{{ act.actions }}</div>
          <div v-if="act.innerVoice" class="voice">「{{ act.innerVoice }}」</div>
        </div>
      </div>
      <button v-if="hasMore" class="more" @click="loadMoreTimeline">加载更多</button>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import LocationIcon from '@/components/sandbox/LocationIcon.vue'
import SandboxMapViewer from '@/components/sandbox/SandboxMapViewer.vue'
import { useMemberStore } from '@/store/member'
import { layoutMarkers, overlapAnchors } from '@/utils/sandboxMap'
import {
  portalSandbox,
  portalSandboxActs,
  portalSandboxCoin,
  portalSandboxInteractions,
  portalSandboxWhisper
} from '@/api/sandbox'

const router = useRouter()
const memberStore = useMemberStore()
const { userInfo } = storeToRefs(memberStore)
const isLogin = computed(() => !!userInfo.value)
const myPoints = computed(() => (userInfo.value ? userInfo.value.points : 0))

const world = reactive({ name: '', description: '', mapImage: '' })
const locations = ref([])
const characters = ref([])
const enabled = ref(true)
const whisperPoints = ref(1)
const coinRate = ref(10)

const activeId = ref(null)
const mapViewerVisible = ref(false)
const filterId = ref(null)
const keyword = ref('')
const collapsed = ref(false)

const whisperTargetId = ref(null)
const whispers = ref([])
const whisperText = ref('')
const sending = ref(false)
const contributePoints = ref(5)
const contributing = ref(false)
/** 当前时间（每 30 秒刷新一次，用于「下次行动」倒计时） */
const now = ref(Date.now())
let clockTimer = null

const timeline = ref([])
const timelinePage = ref(1)
const timelineTotal = ref(0)
const pageSize = 10

const active = computed(() => {
  if (!activeId.value) return null
  return characters.value.find((c) => c.id === activeId.value) || null
})
const hasMore = computed(() => timeline.value.length < timelineTotal.value)

/** 同坐标的角色自动错开，避免叠在一起只看到一个 */
const displayCharacters = computed(() => layoutMarkers(characters.value, 5))
const anchors = computed(() => overlapAnchors(displayCharacters.value))

/** 角色搜索：匹配名字、称号、当前位置、外貌 */
const filteredCharacters = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return characters.value
  return characters.value.filter((c) => {
    return [c.name, c.title, c.locationName, c.appearance]
      .filter(Boolean)
      .some((text) => String(text).toLowerCase().includes(kw))
  })
})

const whisperTargetName = computed(() => {
  const hit = characters.value.find((c) => c.id === whisperTargetId.value)
  return hit ? hit.name : '旅人'
})

function characterName(id) {
  const hit = characters.value.find((c) => c.id === id)
  return hit ? hit.name : `角色#${id}`
}

async function load() {
  const data = await portalSandbox()
  Object.assign(world, data.world || {})
  locations.value = data.locations || []
  characters.value = data.characters || []
  enabled.value = !!data.enabled
  whisperPoints.value = data.whisperPoints == null ? 1 : data.whisperPoints
  coinRate.value = data.coinRate == null ? 10 : data.coinRate
  if (characters.value.length) {
    await selectCharacter(characters.value[0])
  }
  await loadTimeline(true)
}

async function selectCharacter(character) {
  activeId.value = character.id
  filterId.value = character.id
  whisperTargetId.value = character.id
  // 同时刷新角色数据，保证好感度等数值是最新的
  await Promise.all([loadWhispers(), loadTimeline(true), refreshCharacters()])
}

function openMapViewer() {
  if (!world.mapImage) {
    ElMessage.warning('管理员还没有上传地图背景')
    return
  }
  mapViewerVisible.value = true
}

/** 在放大的地图里点角色：选中后关掉全屏，回到页面看档案 */
async function onViewerSelect(character) {
  await selectCharacter(character)
  mapViewerVisible.value = false
}

async function setFilter(id) {
  filterId.value = id
  await loadTimeline(true)
}

async function loadWhispers() {
  if (!whisperTargetId.value) {
    whispers.value = []
    return
  }
  try {
    const data = await portalSandboxInteractions({ characterId: whisperTargetId.value, page: 1, size: 20 })
    whispers.value = (data.list || []).slice().reverse()
  } catch (e) {
    whispers.value = []
  }
}

async function loadTimeline(reset = false) {
  if (reset) {
    timelinePage.value = 1
  }
  const data = await portalSandboxActs({
    characterId: filterId.value || undefined,
    page: timelinePage.value,
    size: pageSize
  })
  timelineTotal.value = data.total || 0
  timeline.value = reset ? data.list || [] : timeline.value.concat(data.list || [])
}

async function loadMoreTimeline() {
  timelinePage.value += 1
  await loadTimeline(false)
}

async function sendWhisper() {
  if (!isLogin.value) {
    ElMessage.warning('登录后才能留下低语')
    router.push('/portal/login')
    return
  }
  if (!whisperTargetId.value) {
    ElMessage.warning('请先选择要互动的角色')
    return
  }
  const content = whisperText.value.trim()
  if (!content) {
    ElMessage.warning('说点什么再发送吧')
    return
  }
  sending.value = true
  try {
    await portalSandboxWhisper({ characterId: whisperTargetId.value, content })
    whisperText.value = ''
    ElMessage.success(`低语已送给「${whisperTargetName.value}」`)
    await loadWhispers()
    await memberStore.fetchInfo(true)
  } finally {
    sending.value = false
  }
}

/** 只刷新地图与角色数据，保留当前选中的角色 */
async function refreshCharacters() {
  const data = await portalSandbox()
  Object.assign(world, data.world || {})
  locations.value = data.locations || []
  characters.value = data.characters || []
  enabled.value = !!data.enabled
  whisperPoints.value = data.whisperPoints == null ? 1 : data.whisperPoints
  coinRate.value = data.coinRate == null ? 10 : data.coinRate
}

function coinTypeText(type) {
  if (type === 'contribute') return '旅人贡献'
  if (type === 'earn') return '赚取'
  if (type === 'spend') return '花销'
  return '管理员调整'
}

/** 好感度 -100~100 映射到进度条宽度 */
function favorPercent(favor) {
  const value = Number(favor || 0)
  return Math.max(0, Math.min(100, (value + 100) / 2))
}

function favorColor(favor) {
  const value = Number(favor || 0)
  if (value < 0) return '#e2664f'
  if (value < 40) return '#b9a5c9'
  if (value < 80) return '#7bc47f'
  return '#f2b23e'
}

function parseTime(text) {
  if (!text) return null
  const date = new Date(String(text).replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

/** 前台展示下一次行动时间：优先显示相对时间，方便判断角色什么时候会动 */
function nextRunText(character) {
  if (!character) return '—'
  if (!enabled.value) return '沙盒已暂停'
  if (character.enabled !== 1) return '该角色已停用自动行动'
  const next = parseTime(character.nextRunTime)
  if (!next) return '待安排'
  const hhmm = String(character.nextRunTime).slice(11, 16)
  const minutes = Math.round((next.getTime() - now.value) / 60000)
  if (minutes <= 0) return `即将行动（${hhmm}）`
  if (minutes < 60) return `约 ${minutes} 分钟后（${hhmm}）`
  const hours = Math.floor(minutes / 60)
  return `约 ${hours} 小时 ${minutes % 60} 分钟后（${hhmm}）`
}

async function onContribute() {
  if (!isLogin.value) {
    ElMessage.warning('登录后才能贡献金币')
    router.push('/portal/login')
    return
  }
  if (!activeId.value) {
    ElMessage.warning('请先选择一个角色')
    return
  }
  const points = Number(contributePoints.value)
  if (!Number.isFinite(points) || points < 1) {
    ElMessage.warning('贡献积分至少要 1')
    return
  }
  contributing.value = true
  try {
    const res = await portalSandboxCoin({ characterId: activeId.value, points })
    ElMessage.success(`已为「${active.value ? active.value.name : '角色'}」贡献 ${res.gained} 金币`)
    await memberStore.fetchInfo(true)
    await refreshCharacters()
  } finally {
    contributing.value = false
  }
}

onMounted(async () => {
  clockTimer = setInterval(() => {
    now.value = Date.now()
  }, 30000)
  try {
    if (memberStore.token) {
      await memberStore.fetchInfo()
    }
    await load()
  } catch (e) {
    // 加载失败时保留空状态
  }
})

onBeforeUnmount(() => {
  if (clockTimer) {
    clearInterval(clockTimer)
    clockTimer = null
  }
})
</script>

<style scoped>
.sandbox {
  max-width: 1320px;
  margin: 0 auto;
  padding: calc(var(--header-height) + 32px) 20px 60px;
}
.sandbox-head { text-align: center; margin-bottom: 22px; }
.sandbox-head h1 {
  margin: 0 0 8px;
  font-size: 30px;
  letter-spacing: 2px;
  color: var(--text-strong);
}
.sandbox-head p { margin: 0; color: var(--text-muted); font-size: 14px; }
.paused {
  display: inline-block;
  margin-top: 12px;
  padding: 4px 14px;
  border-radius: 999px;
  font-size: 12px;
  color: #b07a20;
  background: rgba(255, 200, 90, 0.18);
  border: 1px solid rgba(255, 200, 90, 0.35);
}

.card, .map-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  backdrop-filter: blur(12px);
  margin-bottom: 22px;
}
.map-card { padding: 14px; }
.map-stage {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--accent-soft);
}
.map-bg { width: 100%; height: 100%; object-fit: cover; display: block; }
.map-empty {
  padding: 70px 20px;
  text-align: center;
  color: var(--text-muted);
}
.map-empty .sub { font-size: 12px; opacity: 0.8; }

.loc {
  position: absolute;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  pointer-events: none;
}
.loc-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50% 50% 50% 6px;
  color: #fff;
  background: rgba(0, 0, 0, 0.42);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.25);
}
.loc-name {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 999px;
  color: #fff;
  background: rgba(0, 0, 0, 0.36);
  white-space: nowrap;
}

.actor {
  position: absolute;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  /* 位置变化时平滑移动，而不是瞬间跳过去 */
  transition: left 1.1s ease, top 1.1s ease;
  z-index: 2;
}
.actor:hover { transform: translate(-50%, -50%) scale(1.06); }
.actor.stacked .actor-avatar,
.actor.stacked .actor-fallback { border-color: var(--accent); }
.anchor {
  position: absolute;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  gap: 2px;
  pointer-events: none;
  z-index: 1;
}
.anchor-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.85);
  box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.28);
}
.anchor-count {
  font-size: 10px;
  padding: 0 5px;
  border-radius: 999px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
}
.actor.active .actor-avatar,
.actor.active .actor-fallback { box-shadow: 0 0 0 3px var(--accent), 0 8px 22px rgba(0, 0, 0, 0.3); }
.actor-avatar, .actor-fallback {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  object-fit: cover;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--card-solid);
  color: var(--text-strong);
  font-size: 20px;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.25);
  border: 2px solid rgba(255, 255, 255, 0.85);
}
.actor-name {
  font-size: 12px;
  padding: 1px 9px;
  border-radius: 999px;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  white-space: nowrap;
}
.map-zoom-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 4;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  backdrop-filter: blur(6px);
}
.map-zoom-btn:hover { background: rgba(0, 0, 0, 0.6); }

.panel { padding: 18px 22px 22px; }
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.panel-head h3 { margin: 0; font-size: 15px; color: var(--text-strong); }
.panel-tools { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.panel-body { margin-top: 14px; }
.char-search {
  width: 220px;
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card-solid);
  color: var(--text);
  font-size: 13px;
  outline: none;
}
.char-search:focus { border-color: var(--accent); }
.ghost-btn {
  padding: 7px 16px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
}
.ghost-btn:hover { color: var(--accent); border-color: var(--accent); }

.char-chips {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  padding-bottom: 4px;
  margin-bottom: 8px;
}
.char-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px 6px 6px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--glass-bg);
  color: var(--text);
  cursor: pointer;
  text-align: left;
}
.char-chip.on { border-color: var(--accent); background: var(--accent-soft); }
.chip-avatar { width: 30px; height: 30px; border-radius: 50%; object-fit: cover; }
.chip-avatar.fallback {
  display: flex; align-items: center; justify-content: center;
  background: var(--accent-soft); color: var(--text-strong); font-size: 13px;
}
.chip-text { display: flex; flex-direction: column; line-height: 1.25; }
.chip-name { font-size: 13px; color: var(--text-strong); }
.chip-loc { font-size: 11px; color: var(--text-muted); }

.detail { margin-top: 10px; }
.detail-head { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.detail-avatar { width: 74px; height: 74px; border-radius: 18px; object-fit: cover; }
.detail-title h2 { margin: 0; font-size: 20px; color: var(--text-strong); }
.detail-title .title { margin: 3px 0 0; color: var(--accent); font-size: 13px; }
.muted { color: var(--text-muted); font-size: 13px; margin: 4px 0 0; }
.time { color: var(--text-muted); font-size: 12px; margin-left: 8px; }
.status { margin-left: auto; display: flex; gap: 8px; flex-wrap: wrap; }
.chip {
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-strong);
  background: var(--accent-soft);
  border: 1px solid var(--border);
}
.appearance { margin: 16px 0 0; color: var(--text); font-size: 14px; line-height: 1.9; }
.recent h3 { margin: 20px 0 10px; font-size: 15px; color: var(--text-strong); }
.act {
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  background: var(--glass-bg);
  border: 1px solid var(--border);
  margin-bottom: 10px;
}
.act-meta { display: flex; align-items: center; gap: 10px; font-size: 12px; color: var(--text-muted); }
.act-meta .place { color: var(--accent); }
.act-body { margin-top: 6px; white-space: pre-line; line-height: 1.8; color: var(--text); font-size: 14px; }
.voice { margin-top: 6px; color: var(--accent-2); font-size: 13px; font-style: italic; }

.coin-chip {
  background: rgba(233, 186, 80, 0.22);
  border-color: rgba(233, 186, 80, 0.45);
  color: #d9a441;
  font-weight: 600;
}
.contribute {
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  background: var(--glass-bg);
  border: 1px solid var(--border);
}
.contribute-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.contribute-head strong { font-size: 14px; color: var(--text-strong); }
.contribute-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
.num-input {
  width: 84px;
  padding: 7px 10px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card-solid);
  color: var(--text);
  font-size: 13px;
  text-align: center;
}
.coin-history { margin-top: 16px; }
.coin-history h3 { margin: 0 0 8px; font-size: 15px; color: var(--text-strong); }
.coin-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px dashed var(--border);
  font-size: 13px;
}
.coin-type { color: var(--text-muted); min-width: 66px; }
.coin-delta { font-weight: 600; }
.coin-delta.plus { color: #4caf62; }
.coin-delta.minus { color: #e2664f; }
.coin-delta.inline { margin-left: 10px; font-size: 12px; }
.coin-remark { color: var(--text); flex: 1; }
.companion-tag {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  color: #c98a2a;
  background: rgba(233, 186, 80, 0.18);
  border: 1px solid rgba(233, 186, 80, 0.4);
  white-space: nowrap;
}
.companion-tag.inline { margin-left: 10px; }
.favor-tag {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  color: #c95a82;
  background: rgba(255, 111, 159, 0.16);
  border: 1px solid rgba(255, 111, 159, 0.38);
  white-space: nowrap;
}
.favor-tag.inline { margin-left: 8px; }
.react-tag {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  color: #7f9bd1;
  background: rgba(127, 155, 209, 0.18);
  border: 1px solid rgba(127, 155, 209, 0.4);
  white-space: nowrap;
}
.react-tag.inline { margin-left: 8px; }
.relations { margin-top: 18px; }
.relations h3 { margin: 0 0 10px; font-size: 15px; color: var(--text-strong); }
.relation-item {
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--glass-bg);
  border: 1px solid var(--border);
  margin-bottom: 10px;
}
.relation-avatar { width: 38px; height: 38px; border-radius: 12px; object-fit: cover; flex-shrink: 0; }
.relation-avatar.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent-soft);
  color: var(--text-strong);
  font-size: 15px;
}
.relation-main { flex: 1; min-width: 0; }
.relation-name { font-size: 14px; color: var(--text-strong); }
.relation-name .muted { margin-left: 8px; font-size: 12px; }
.relation-bar { display: flex; align-items: center; gap: 10px; margin-top: 6px; }
.relation-track {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--accent-soft);
  overflow: hidden;
}
.relation-fill { height: 100%; border-radius: 999px; transition: width 0.6s ease; }
.relation-value { font-size: 12px; color: var(--text-muted); white-space: nowrap; }
.favor-delta {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
  white-space: nowrap;
}
.favor-delta.plus { color: #4caf62; background: rgba(76, 175, 98, 0.16); }
.favor-delta.minus { color: #e2664f; background: rgba(226, 102, 79, 0.16); }
.relation-reverse, .relation-remark { margin-top: 4px; font-size: 12px; color: var(--text-muted); }

.target-label { font-size: 13px; color: var(--text-muted); }
.target-select {
  width: 200px;
}
.target-select :deep(.el-select__wrapper) {
  border-radius: 999px;
  font-size: 13px;
}
.option-name { margin-right: 10px; }
.option-loc { color: var(--text-muted); font-size: 12px; }

.whisper-list { margin: 12px 0; }
.whisper-item { display: flex; gap: 10px; padding: 10px 0; border-bottom: 1px dashed var(--border); }
.whisper-avatar { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }
.whisper-avatar.fallback {
  display: flex; align-items: center; justify-content: center;
  background: var(--accent-soft); color: var(--text-strong); font-size: 13px;
}
.whisper-user { font-size: 13px; color: var(--text-strong); }
.whisper-text { font-size: 14px; color: var(--text); line-height: 1.7; margin-top: 2px; }
.whisper-form { display: flex; gap: 10px; align-items: flex-end; }
.whisper-form textarea {
  flex: 1;
  min-height: 68px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--card-solid);
  color: var(--text);
  font-family: inherit;
  font-size: 14px;
  resize: vertical;
}
.send, .more {
  padding: 10px 18px;
  border: none;
  border-radius: var(--radius-sm);
  background: linear-gradient(120deg, var(--accent), var(--accent-2));
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.send:disabled { opacity: 0.6; cursor: not-allowed; }

.timeline { padding: 22px; }
.timeline-head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; }
.timeline-head h3 { margin: 0; font-size: 15px; color: var(--text-strong); }
.filters { display: flex; gap: 8px; flex-wrap: wrap; }
.chip-btn {
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
}
.chip-btn.on { background: var(--accent-soft); color: var(--accent); border-color: transparent; }
.timeline-item { display: flex; gap: 14px; padding: 14px 0; border-bottom: 1px dashed var(--border); }
.timeline-time { width: 150px; flex-shrink: 0; color: var(--text-muted); font-size: 12px; padding-top: 3px; }
.timeline-content { flex: 1; }
.timeline-title { font-size: 14px; color: var(--text-strong); }
.timeline-title .muted { margin-left: 8px; font-size: 12px; }
.more { display: block; margin: 18px auto 0; }

@media (max-width: 720px) {
  .sandbox { padding: calc(var(--header-height) + 20px) 12px 50px; }
  .detail-head { align-items: flex-start; }
  .status { margin-left: 0; width: 100%; }
  .timeline-item { flex-direction: column; gap: 4px; }
  .timeline-time { width: auto; }
  .actor-avatar, .actor-fallback { width: 40px; height: 40px; font-size: 16px; }
  .whisper-form { flex-direction: column; align-items: stretch; }
  .char-search { width: 150px; }
  .panel { padding: 16px 14px 18px; }
}
</style>
