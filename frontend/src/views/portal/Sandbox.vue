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
      <div v-else class="map-stage" @click="activeLocationId = null">
        <img class="map-bg" :src="world.mapImage" alt="地图" />
        <!-- 旅人纪闻：当天世界上发生的大事 -->
        <div v-if="newsTitle && news.length" class="news-card" :class="{ collapsed: newsCollapsed }" @click.stop>
          <div class="news-head" @click="newsCollapsed = !newsCollapsed">
            <span class="news-name">{{ newsTitle }}</span>
            <span class="news-date">{{ todayText }}</span>
            <span class="news-toggle">{{ newsCollapsed ? '展开' : '收起' }}</span>
          </div>
          <div v-show="!newsCollapsed" class="news-list">
            <button
              v-for="item in news"
              :key="'news-' + item.id"
              type="button"
              class="news-item"
              :title="item.content || item.title"
              @click="focusNews(item)"
            >
              <span class="news-level" :class="'lv-' + (item.level || 1)"></span>
              <span class="news-text">{{ item.title }}</span>
              <span v-if="item.locationName" class="news-place">{{ item.locationName }}</span>
            </button>
          </div>
        </div>

        <!-- 地点区域：默认只显示地名与图标，不显示角色头像 -->
        <div
          v-for="loc in locations"
          :key="'area-' + loc.id"
          class="area"
          :class="{ on: activeLocationId === loc.id, flash: flashLocationId === loc.id }"
          :style="areaStyle(loc)"
          :title="loc.description || loc.name"
          @click.stop="toggleLocation(loc)"
        >
          <span class="area-label">
            <LocationIcon :icon="loc.icon" :size="15" />
            <span class="area-name">{{ loc.name }}</span>
          </span>
          <span v-if="charactersIn(loc).length" class="area-count">{{ charactersIn(loc).length }}</span>
        </div>

        <!-- 点击地点后，该地的角色头像出现在区域下方 -->
        <div v-if="activeLocation" class="area-actors" :style="actorsStyle" @click.stop>
          <button
            v-for="member in charactersIn(activeLocation)"
            :key="'aa-' + member.id"
            type="button"
            class="area-actor"
            :class="{ on: activeId === member.id }"
            :title="`${member.name} · ${placeText(member.locationName, member.subLocation)}`"
            @click="selectFromLocation(member)"
          >
            <img v-if="member.avatar" class="area-actor-img" :src="member.avatar" :alt="member.name" />
            <span v-else class="area-actor-img fallback">{{ (member.name || '?').slice(0, 1) }}</span>
            <span class="area-actor-name">{{ member.name }}</span>
          </button>
          <span v-if="!charactersIn(activeLocation).length" class="area-empty">这里暂时没有角色</span>
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
      :characters="characters"
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
              <p class="muted">当前位置：{{ characterPlace(active) }}</p>
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

          <div class="backpack">
            <div class="backpack-head">
              <h3>背包</h3>
              <span class="backpack-meta">
                {{ (active.items || []).length }} 种 · 共 {{ totalItemCount }} 件
              </span>
            </div>
            <div v-if="(active.items || []).length" class="item-grid">
              <button
                v-for="item in active.items"
                :key="'item-' + item.id"
                type="button"
                class="item-slot"
                :class="['rarity-' + (item.rarity || 1), { on: selectedItem && selectedItem.id === item.id }]"
                :title="item.name"
                @click="selectedItem = item"
              >
                <span class="slot-icon">
                  <img v-if="item.icon" :src="item.icon" :alt="item.name" />
                  <template v-else>{{ emojiForItem(item.name) }}</template>
                </span>
                <span class="slot-name">{{ item.name }}</span>
                <span v-if="(item.quantity || 1) > 1" class="slot-qty">{{ item.quantity }}</span>
              </button>
            </div>
            <div v-else class="backpack-empty">背包空空的，等它出门捡点什么吧</div>

            <div
              v-if="selectedItem"
              class="item-detail"
              :class="'rarity-' + (selectedItem.rarity || 1)"
            >
              <span class="detail-item-icon">
                <img v-if="selectedItem.icon" :src="selectedItem.icon" :alt="selectedItem.name" />
                <template v-else>{{ emojiForItem(selectedItem.name) }}</template>
              </span>
              <div class="detail-main">
                <div class="detail-name">
                  {{ selectedItem.name }}
                  <span class="rarity-tag">{{ rarityMeta(selectedItem.rarity).name }}</span>
                </div>
                <div class="detail-qty">数量：{{ selectedItem.quantity || 1 }}</div>
                <div class="detail-desc">
                  {{ selectedItem.description || '还没有关于这件物品的说明。' }}
                </div>
              </div>
            </div>
          </div>

          <div v-if="(active.recentMemories || []).length" class="memories">
            <h3>最近的记忆</h3>
            <div v-for="memory in active.recentMemories" :key="'mem-' + memory.id" class="memory-item">
              <div class="memory-date">{{ memory.memoryDate }}</div>
              <div class="memory-text">{{ memory.summary }}</div>
            </div>
          </div>

          <div class="recent">
            <h3>最近的行动</h3>
            <div v-if="!(active.recentActs || []).length" class="muted">还没有行动记录</div>
            <div v-for="act in active.recentActs || []" :key="act.id" class="act">
              <div class="act-meta">
                <span class="time">{{ act.createTime }}</span>
                <span class="place">{{ placeText(act.locationName, act.subLocation) }}</span>
                <span v-if="act.companions" class="companion-tag">与 {{ act.companions }} 互动</span>
                <span v-if="act.favorChange" class="favor-tag">好感 {{ act.favorChange }}</span>
                <span v-if="act.itemChange" class="item-tag">{{ act.itemChange }}</span>
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
          <el-select
            v-model="locationFilter"
            class="loc-filter"
            clearable
            placeholder="全部地点"
            @change="loadTimeline(true)"
          >
            <el-option v-for="loc in locations" :key="'lf-' + loc.id" :label="loc.name" :value="loc.name" />
          </el-select>
        </div>
      </div>
      <div v-if="!timeline.length" class="muted">还没有行动记录</div>
      <div v-for="act in timeline" :key="act.id" class="timeline-item">
        <div class="timeline-time">{{ act.createTime }}</div>
        <div class="timeline-content">
          <div class="timeline-title">
            <strong>{{ characterName(act.characterId) }}</strong>
            <span class="muted">在 {{ placeText(act.locationName, act.subLocation) }}</span>
            <span v-if="act.coinChange" class="coin-delta inline" :class="act.coinChange > 0 ? 'plus' : 'minus'">
              {{ act.coinChange > 0 ? '+' : '' }}{{ act.coinChange }} 金币
            </span>
            <span v-if="act.companions" class="companion-tag inline">与 {{ act.companions }} 互动</span>
            <span v-if="act.favorChange" class="favor-tag inline">好感 {{ act.favorChange }}</span>
            <span v-if="act.reaction === 1" class="react-tag inline">回应</span>
            <span v-if="act.itemChange" class="item-tag inline">{{ act.itemChange }}</span>
            <span v-if="act.newsRef" class="news-tag inline">听闻 · {{ act.newsRef }}</span>
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
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import LocationIcon from '@/components/sandbox/LocationIcon.vue'
import SandboxMapViewer from '@/components/sandbox/SandboxMapViewer.vue'
import { useMemberStore } from '@/store/member'
import { emojiForItem, rarityMeta } from '@/utils/sandboxItems'
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
/** 旅人纪闻（当天世界大事） */
const news = ref([])
const newsTitle = ref('')
const newsCollapsed = ref(false)
/** 被纪闻点中的地点会闪烁高亮 */
const flashLocationId = ref(null)

const activeId = ref(null)
const mapViewerVisible = ref(false)
/** 当前展开的地点（点地名后显示该地的角色） */
const activeLocationId = ref(null)
/** 背包里被选中的物品（点击格子后展示详情） */
const selectedItem = ref(null)
const filterId = ref(null)
/** 时间线按一级地点筛选 */
const locationFilter = ref('')
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
/** 背包物品总件数 */
const totalItemCount = computed(() => {
  const items = (active.value && active.value.items) || []
  return items.reduce((sum, item) => sum + (Number(item.quantity) || 1), 0)
})

// 切换角色时清掉上一件选中的物品
watch(activeId, () => {
  selectedItem.value = null
})

/** 当前展开的地点（同一时间只展开一个） */
const activeLocation = computed(() => locations.value.find((loc) => loc.id === activeLocationId.value) || null)

/** 地点区域：x,y 为左上角，width/height 为宽高（0 表示单点） */
function areaRect(location) {
  const left = Number(location.x == null ? 50 : location.x)
  const top = Number(location.y == null ? 50 : location.y)
  return {
    left,
    top,
    width: Math.max(0, Number(location.width || 0)),
    height: Math.max(0, Number(location.height || 0))
  }
}

/** 区域显示样式（单点也留一点最小尺寸，方便点击） */
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

/** 角色属于哪个地点：先按角色记录的地点名匹配，再用坐标落在哪个区域里兜底 */
function locationOf(character) {
  const byName = locations.value.find((loc) => loc.name === character.locationName)
  if (byName) {
    return byName
  }
  const x = Number(character.x == null ? 50 : character.x)
  const y = Number(character.y == null ? 50 : character.y)
  return locations.value.find((loc) => inArea(loc, x, y)) || null
}

function charactersIn(location) {
  if (!location) {
    return []
  }
  return characters.value.filter((character) => {
    const hit = locationOf(character)
    return hit && hit.id === location.id
  })
}

function toggleLocation(location) {
  activeLocationId.value = activeLocationId.value === location.id ? null : location.id
}

/** 点纪闻条目：如果事件发生在某个已知地点，就把那个地点展开 */
function focusNews(item) {
  const location = locations.value.find((loc) => loc.name === item.locationName)
  if (location) {
    activeLocationId.value = location.id
    // 闪烁高亮一下事件发生地，便于一眼找到
    flashLocationId.value = location.id
    setTimeout(() => {
      if (flashLocationId.value === location.id) {
        flashLocationId.value = null
      }
    }, 1700)
  }
}

/** 今天日期（纪闻卡片右上角） */
const todayText = computed(() => {
  const now = new Date()
  return `${now.getMonth() + 1}月${now.getDate()}日`
})

async function selectFromLocation(character) {
  await selectCharacter(character)
}

/** 角色头像行的位置：默认贴在区域下方，靠下时改到区域上方 */
const actorsStyle = computed(() => {
  const location = activeLocation.value
  if (!location) {
    return {}
  }
  const rect = areaRect(location)
  const below = rect.top + rect.height < 68
  return {
    left: Math.min(72, Math.max(0, rect.left - 2)) + '%',
    top: (below ? rect.top + Math.max(rect.height, 2.2) : rect.top) + '%',
    transform: below ? 'translateY(8px)' : 'translateY(calc(-100% - 8px))'
  }
})

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

/** 地点显示：一级地点 · 二级地点 */
function placeText(locationName, subLocation) {
  if (!locationName && !subLocation) {
    return '某处'
  }
  return subLocation ? `${locationName || '某处'} · ${subLocation}` : locationName
}

/** 角色当前位置（都没有时显示「尚未行动」） */
function characterPlace(character) {
  if (!character) return '尚未行动'
  if (!character.locationName && !character.subLocation) return '尚未行动'
  return placeText(character.locationName, character.subLocation)
}


async function load() {
  const data = await portalSandbox()
  Object.assign(world, data.world || {})
  locations.value = data.locations || []
  characters.value = data.characters || []
  enabled.value = !!data.enabled
  whisperPoints.value = data.whisperPoints == null ? 1 : data.whisperPoints
  coinRate.value = data.coinRate == null ? 10 : data.coinRate
  news.value = data.news || []
  newsTitle.value = data.newsTitle || ''
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
    locationName: locationFilter.value || undefined,
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
  news.value = data.news || []
  newsTitle.value = data.newsTitle || ''
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
  // 带上 AI 给出的原因，例如「正在睡觉，约 6 小时后（07:00）」
  const reason = character.nextReason ? `正在${character.nextReason}，` : ''
  if (minutes <= 0) return `${reason}即将行动（${hhmm}）`
  if (minutes < 60) return `${reason}约 ${minutes} 分钟后（${hhmm}）`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest
    ? `${reason}约 ${hours} 小时 ${rest} 分钟后（${hhmm}）`
    : `${reason}约 ${hours} 小时后（${hhmm}）`
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
  // 手机上默认收起纪闻卡片，避免挡住地图
  newsCollapsed.value = window.innerWidth < 720
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

/* 地点区域：半透明色块 + 虚线边框，默认只显示图标与地名 */
/* 旅人纪闻：地图左上角的悬浮卡片 */
.news-card {
  position: absolute;
  left: 12px;
  top: 12px;
  z-index: 5;
  width: 230px;
  max-width: 46%;
  padding: 8px 10px;
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.46);
  backdrop-filter: blur(7px);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.28);
  color: #fff;
}
.news-head {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}
.news-name {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
}
.news-date { font-size: 11px; opacity: 0.7; }
.news-toggle { margin-left: auto; font-size: 11px; opacity: 0.75; }
.news-list {
  margin-top: 6px;
  max-height: 160px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.news-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 0;
  border: none;
  background: transparent;
  color: #fff;
  text-align: left;
  cursor: pointer;
  font-size: 12px;
  line-height: 1.5;
}
.news-item:hover .news-text { color: var(--accent); }
.news-level {
  flex-shrink: 0;
  width: 7px;
  height: 7px;
  margin-top: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.55);
}
.news-level.lv-2 { background: #f2b23e; box-shadow: 0 0 8px rgba(242, 178, 62, 0.8); }
.news-level.lv-3 { background: #ff6f6f; box-shadow: 0 0 9px rgba(255, 111, 111, 0.9); }
.news-text { flex: 1; }
.news-place {
  flex-shrink: 0;
  font-size: 10px;
  padding: 0 5px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  white-space: nowrap;
}
.news-card.collapsed { width: auto; }

.area {
  position: absolute;
  padding: 2px 4px;
  border: 1.5px dashed rgba(255, 255, 255, 0.75);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.16);
  cursor: pointer;
  overflow: hidden;
  transition: background 0.25s ease, border-color 0.25s ease, box-shadow 0.25s ease;
}
.area:hover { background: rgba(255, 255, 255, 0.26); }
.area.on {
  border-color: var(--accent);
  background: var(--accent-soft);
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.35), 0 8px 24px rgba(0, 0, 0, 0.25);
}
.area-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.42);
  white-space: nowrap;
  overflow: hidden;
}
.area-name { overflow: hidden; text-overflow: ellipsis; }
.area-count {
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
  background: linear-gradient(120deg, var(--accent), var(--accent-2));
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
}

/* 点击地点后展开的角色头像行 */
.area-actors {
  position: absolute;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  max-width: 72%;
  padding: 6px 8px;
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.42);
  backdrop-filter: blur(6px);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.28);
}
.area-actor {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
}
.area-actor-img {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba(255, 255, 255, 0.9);
  background: var(--card-solid);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  color: var(--text-strong);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.area-actor:hover .area-actor-img { transform: translateY(-2px); }
.area-actor.on .area-actor-img { box-shadow: 0 0 0 3px var(--accent); }
.area-actor-name { font-size: 11px; color: #fff; white-space: nowrap; }
.area-empty { font-size: 12px; color: rgba(255, 255, 255, 0.75); }
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
.item-tag {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  color: #4f9d8f;
  background: rgba(79, 157, 143, 0.16);
  border: 1px solid rgba(79, 157, 143, 0.38);
  white-space: nowrap;
}
.item-tag.inline { margin-left: 8px; }
.news-tag {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  color: #c98a2a;
  background: rgba(233, 186, 80, 0.16);
  border: 1px solid rgba(233, 186, 80, 0.4);
  white-space: nowrap;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: inline-block;
  vertical-align: bottom;
}
.news-tag.inline { margin-left: 8px; }
.area.flash {
  border-color: #ff6f9f;
  animation: areaFlash 0.8s ease-in-out 2;
}
@keyframes areaFlash {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 111, 159, 0); }
  50% { box-shadow: 0 0 0 7px rgba(255, 111, 159, 0.55); }
}
/* 背包：二次元游戏风格的物品格子 */
.backpack { margin-top: 18px; }
.backpack h3, .memories h3 { margin: 0 0 10px; font-size: 15px; color: var(--text-strong); }
.backpack-head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; }
.backpack-head h3 { margin-bottom: 10px; }
.backpack-meta { font-size: 12px; color: var(--text-muted); }
.item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(78px, 1fr));
  gap: 10px;
}
.item-slot {
  position: relative;
  aspect-ratio: 1 / 1.12;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px 4px;
  border-radius: 14px;
  border: 1.5px solid var(--border);
  background: var(--glass-bg);
  color: var(--text-strong);
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}
.item-slot::before {
  /* 斜向高光，模仿游戏道具格的质感 */
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(140deg, rgba(255, 255, 255, 0.22), transparent 55%);
  pointer-events: none;
}
.item-slot:hover { transform: translateY(-3px); }
.item-slot.on { transform: translateY(-3px); }
.slot-icon {
  font-size: 26px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
}
.slot-icon img { width: 34px; height: 34px; object-fit: contain; }
.slot-name {
  max-width: 100%;
  font-size: 11px;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.slot-qty {
  position: absolute;
  right: 5px;
  bottom: 5px;
  min-width: 18px;
  padding: 0 5px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}
.backpack-empty {
  padding: 18px;
  border-radius: var(--radius-sm);
  border: 1px dashed var(--border);
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}
.item-detail {
  display: flex;
  gap: 14px;
  margin-top: 12px;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  border: 1.5px solid var(--border);
  background: var(--glass-bg);
}
.detail-item-icon {
  font-size: 34px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  flex-shrink: 0;
}
.detail-item-icon img { width: 44px; height: 44px; object-fit: contain; }
.detail-main { flex: 1; min-width: 0; }
.detail-name { font-size: 15px; color: var(--text-strong); display: flex; align-items: center; gap: 8px; }
.rarity-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
}
.detail-qty { margin-top: 4px; font-size: 12px; color: var(--text-muted); }
.detail-desc { margin-top: 6px; font-size: 13px; color: var(--text); line-height: 1.8; }

/* 品质配色（1 普通 → 5 传说） */
.rarity-1 { border-color: rgba(185, 179, 201, 0.45); background: rgba(185, 179, 201, 0.14); }
.rarity-1 .rarity-tag { color: #9c95ae; background: rgba(185, 179, 201, 0.22); }
.rarity-2 { border-color: rgba(99, 192, 122, 0.55); background: rgba(99, 192, 122, 0.15); box-shadow: 0 4px 14px rgba(99, 192, 122, 0.16); }
.rarity-2 .rarity-tag { color: #63c07a; background: rgba(99, 192, 122, 0.22); }
.rarity-3 { border-color: rgba(91, 155, 213, 0.6); background: rgba(91, 155, 213, 0.16); box-shadow: 0 4px 16px rgba(91, 155, 213, 0.2); }
.rarity-3 .rarity-tag { color: #5b9bd5; background: rgba(91, 155, 213, 0.22); }
.rarity-4 { border-color: rgba(168, 117, 224, 0.65); background: rgba(168, 117, 224, 0.18); box-shadow: 0 4px 18px rgba(168, 117, 224, 0.24); }
.rarity-4 .rarity-tag { color: #a875e0; background: rgba(168, 117, 224, 0.24); }
.rarity-5 { border-color: rgba(240, 177, 60, 0.7); background: rgba(240, 177, 60, 0.2); box-shadow: 0 4px 20px rgba(240, 177, 60, 0.28); }
.rarity-5 .rarity-tag { color: #f0b13c; background: rgba(240, 177, 60, 0.26); }
.rarity-5 .slot-icon, .item-detail.rarity-5 .detail-item-icon { text-shadow: 0 0 12px rgba(240, 177, 60, 0.7); }
.memories { margin-top: 18px; }
.memory-item {
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: var(--glass-bg);
  border: 1px solid var(--border);
  margin-bottom: 10px;
}
.memory-date { font-size: 12px; color: var(--accent); margin-bottom: 4px; }
.memory-text { font-size: 13.5px; color: var(--text); line-height: 1.85; white-space: pre-line; }
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
.loc-filter { width: 150px; }
.loc-filter :deep(.el-select__wrapper) { border-radius: 999px; font-size: 12px; }
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
  .area-label { font-size: 11px; padding: 1px 6px; }
  .area-actors { max-width: 88%; gap: 8px; }
  .area-actor-img { width: 36px; height: 36px; font-size: 15px; }
  .whisper-form { flex-direction: column; align-items: stretch; }
  .char-search { width: 150px; }
  .panel { padding: 16px 14px 18px; }
}
</style>
