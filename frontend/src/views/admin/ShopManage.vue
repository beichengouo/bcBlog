<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>旅人集市</span>
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
          <el-button @click="openAdd">手动上架</el-button>
        </div>
      </div>
    </template>

    <el-alert
      class="tips"
      type="info"
      :closable="false
      "
      title="集市会按你设置的「间隔 + 起始时间」自动刷新新商品；前台只展示最新一批。用户花积分买下商品后会直接赠送给某个角色（商品进角色背包），角色下一次行动时会收到「来自异世界的礼物」。"
    />

    <div class="stat-row">
      <el-tag type="warning" effect="plain">今天卖出 {{ stats.sold || 0 }} 件</el-tag>
      <el-tag type="success" effect="plain">回收积分 {{ stats.points || 0 }}</el-tag>
      <el-tag effect="plain">当前批次 {{ batchText }}</el-tag>
    </div>

    <el-table :data="items" v-loading="loading" size="small" class="mt">
      <el-table-column label="商品" min-width="200">
        <template #default="{ row }">
          <div class="item-cell">
            <span class="item-emoji">{{ emojiForItem(row.name) }}</span>
            <div>
              <div class="item-name" :style="{ color: rarityMeta(row.rarity).color }">
                {{ row.name }}
                <span class="item-rarity">{{ rarityMeta(row.rarity).name }}</span>
              </div>
              <div class="item-desc">{{ row.description || '（没有描述）' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="价格" width="90">
        <template #default="{ row }">{{ row.price }} 积分</template>
      </el-table-column>
      <el-table-column label="库存" width="110">
        <template #default="{ row }">
          <span :class="{ soldout: row.stock <= 0 }">{{ row.stock }} / {{ row.totalStock }}</span>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.source === 'ai' ? 'primary' : 'warning'" effect="plain">
            {{ row.source === 'ai' ? 'AI' : '管理员' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上架" width="90">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="onToggleEnabled(row, $event)" />
        </template>
      </el-table-column>
      <el-table-column label="置顶" width="90">
        <template #default="{ row }">
          <el-switch :model-value="row.pinned === 1" @change="onTogglePinned(row, $event)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !items.length" description="这一批还没有商品，可以点右上角生成一批" :image-size="70" />

    <!-- 生成与刷新设置 -->
    <el-divider content-position="left">生成与刷新设置</el-divider>
    <el-form :model="settings" label-width="130px" class="setting-form">
      <el-form-item label="集市开关">
        <el-switch v-model="settings.shopEnabled" active-value="1" inactive-value="0" />
        <span class="tip">关闭后前台不显示集市（数据保留）</span>
      </el-form-item>
      <el-form-item label="前台栏目名">
        <el-input v-model="settings.shopTitle" style="width: 200px" maxlength="20" />
      </el-form-item>
      <el-form-item label="自动刷新">
        <el-switch v-model="settings.shopAutoEnabled" active-value="1" inactive-value="0" />
        <span class="tip">开启后按下面的间隔自动刷新</span>
      </el-form-item>
      <el-form-item label="刷新间隔 / 首次时间">
        <el-input v-model="settings.shopIntervalHours" style="width: 90px" />
        <span class="range-sep">小时 · 当天首次</span>
        <el-input v-model="settings.shopAutoTime" style="width: 90px" placeholder="08:00" />
        <span class="tip">
          间隔填 24 就是每天一次；填 6 就是 08:00 / 14:00 / 20:00 / 02:00 一天四次
        </span>
      </el-form-item>
      <el-form-item label="每次生成件数">
        <el-input v-model="settings.shopPerGenerate" style="width: 90px" />
        <span class="tip">1~10 件，价格与库存由 AI 决定后按品质夹取</span>
      </el-form-item>
      <el-form-item label="生成服务商 / 模型">
        <el-select v-model="settings.shopProviderId" clearable placeholder="留空用系统服务商" style="width: 200px">
          <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="String(p.id)" />
        </el-select>
        <el-select
          v-model="settings.shopModel"
          filterable
          allow-create
          clearable
          :loading="shopModelLoading"
          placeholder="选择或输入模型"
          style="width: 240px; margin-left: 8px"
        >
          <el-option v-for="m in shopModels" :key="m" :label="m" :value="m" />
        </el-select>
        <el-button style="margin-left: 8px" :loading="shopModelLoading" @click="loadShopModels">获取模型</el-button>
        <span class="tip">从所选服务商拉取模型列表；也可以直接手填模型名</span>
      </el-form-item>
      <el-form-item label="附加要求">
        <el-input
          v-model="settings.shopPromptExtra"
          type="textarea"
          :rows="2"
          style="width: 520px"
          maxlength="300"
          placeholder="例如：多出现一些食物与药品；不要出现武器"
        />
      </el-form-item>
      <el-form-item label="每人限购">
        <el-input v-model="settings.shopLimitPerCharacter" style="width: 90px" />
        <span class="tip">同一用户对同一商品、每个角色的限购数量（默认 1）</span>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="savingSetting" @click="onSaveSettings">保存设置</el-button>
      </el-form-item>
    </el-form>

    <!-- 购买记录：默认收起 -->
    <el-collapse class="mt">
      <el-collapse-item name="orders">
        <template #title>
          <span class="collapse-title">购买记录</span>
          <span class="tip" style="margin-left: 10px">谁把什么送给了哪个角色（默认收起，点击展开）</span>
        </template>
        <el-table :data="orders" v-loading="loadingOrders" size="small">
          <el-table-column prop="createTime" label="时间" width="170" />
          <el-table-column prop="itemName" label="商品" min-width="140" />
          <el-table-column label="赠送者" width="140">
            <template #default="{ row }">{{ row.userName || '—' }}</template>
          </el-table-column>
          <el-table-column label="收礼角色" width="140">
            <template #default="{ row }">{{ row.characterName || ('角色#' + row.characterId) }}</template>
          </el-table-column>
          <el-table-column label="消耗积分" width="100">
            <template #default="{ row }">{{ row.pointsCost || 0 }}</template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="orderTotal > orderSize"
          class="mt"
          layout="prev, pager, next"
          :total="orderTotal"
          :page-size="orderSize"
          :current-page="orderPage"
          @current-change="onOrderPage"
        />
      </el-collapse-item>
    </el-collapse>

    <!-- 手动上架 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑商品' : '手动上架商品'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称">
          <el-input v-model="form.name" maxlength="60" placeholder="如：暖手炉" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="300" placeholder="一句带故事感的说明" />
        </el-form-item>
        <el-form-item label="品质">
          <el-select v-model="form.rarity" style="width: 140px">
            <el-option v-for="r in ITEM_RARITIES" :key="r.value" :label="r.name" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格 / 库存">
          <el-input-number v-model="form.price" :min="0" :max="999" controls-position="right" />
          <span class="range-sep">积分 · 库存</span>
          <el-input-number v-model="form.stock" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="上架 / 置顶">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
          <el-switch v-model="form.pinned" :active-value="1" :inactive-value="0" style="margin-left: 12px" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { emojiForItem, rarityMeta, ITEM_RARITIES } from '@/utils/sandboxItems'
import { useSandboxWorld } from '@/composables/useSandboxWorld'
import { aiProviderList, aiProviderModels } from '@/api/ai'
import {
  deleteSandboxShopItem,
  generateSandboxShop,
  sandboxSettings,
  sandboxShop,
  sandboxShopOrders,
  sandboxShopStats,
  sandboxWorlds,
  saveSandboxSettings,
  saveSandboxShopItem
} from '@/api/sandbox'

const worlds = ref([])
const { currentWorldId, setCurrentWorld } = useSandboxWorld()
const selectedWorldId = ref(null)
const items = ref([])
const loading = ref(false)
const generating = ref(false)
const saving = ref(false)
const savingSetting = ref(false)
const providers = ref([])
/** 生成商品可用的模型（从所选服务商拉取，也可以手填） */
const shopModels = ref([])
const shopModelLoading = ref(false)
const stats = ref({})
const orders = ref([])
const orderTotal = ref(0)
const orderPage = ref(1)
const orderSize = 10
const loadingOrders = ref(false)
const dialogVisible = ref(false)

const settings = reactive({
  shopTitle: '旅人集市',
  shopEnabled: '1',
  shopAutoEnabled: '1',
  shopIntervalHours: '24',
  shopAutoTime: '08:00',
  shopPerGenerate: '3',
  shopProviderId: '',
  shopModel: '',
  shopPromptExtra: '',
  shopLimitPerCharacter: '1'
})

const form = reactive({
  id: null,
  name: '',
  description: '',
  rarity: 1,
  price: 3,
  stock: 2,
  enabled: 1,
  pinned: 0
})

const batchText = computed(() => {
  const first = items.value[0]
  return first && first.batchTime ? first.batchTime : '（还没有商品）'
})

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
    items.value = (await sandboxShop(selectedWorldId.value)) || []
    stats.value = (await sandboxShopStats(selectedWorldId.value)) || {}
    const s = await sandboxSettings()
    for (const key of Object.keys(settings)) {
      if (s && s[key] != null) {
        settings[key] = s[key]
      }
    }
    // 已选服务商时顺手拉一次模型列表，方便直接选
    if (settings.shopProviderId) {
      shopModels.value = (await aiProviderModels(Number(settings.shopProviderId))) || []
    }
  } finally {
    loading.value = false
  }
}

async function loadOrders() {
  loadingOrders.value = true
  try {
    const data = await sandboxShopOrders({
      worldId: selectedWorldId.value || undefined,
      page: orderPage.value,
      size: orderSize
    })
    orders.value = data.list || []
    orderTotal.value = data.total || 0
  } finally {
    loadingOrders.value = false
  }
}

function onOrderPage(page) {
  orderPage.value = page
  loadOrders()
}

/**
 * 从所选服务商拉取模型列表（和其它模块的"获取模型"一致）。
 * 没选服务商时提示先选；拉不到也可以直接手填模型名（下拉支持 allow-create）。
 */
async function loadShopModels() {
  if (!settings.shopProviderId) {
    ElMessage.warning('请先选择生成用的服务商（留空表示用系统服务商）')
    return
  }
  shopModelLoading.value = true
  try {
    shopModels.value = (await aiProviderModels(Number(settings.shopProviderId))) || []
    if (!shopModels.value.length) {
      ElMessage.warning('没有获取到模型，可手动输入模型名')
    } else {
      ElMessage.success(`已获取 ${shopModels.value.length} 个模型`)
    }
  } finally {
    shopModelLoading.value = false
  }
}

async function onSwitchWorld(id) {
  setCurrentWorld(id)
  orderPage.value = 1
  await load()
  await loadOrders()
}

async function onGenerate() {
  generating.value = true
  try {
    const count = await generateSandboxShop({
      worldId: selectedWorldId.value || undefined,
      providerId: settings.shopProviderId || undefined,
      model: settings.shopModel || undefined
    })
    ElMessage.success(`已生成 ${count} 件商品`)
    await load()
  } finally {
    generating.value = false
  }
}

function openAdd() {
  Object.assign(form, { id: null, name: '', description: '', rarity: 1, price: 3, stock: 2, enabled: 1, pinned: 0 })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    description: row.description || '',
    rarity: row.rarity || 1,
    price: row.price == null ? 1 : row.price,
    stock: row.stock == null ? 0 : row.stock,
    enabled: row.enabled == null ? 1 : row.enabled,
    pinned: row.pinned == null ? 0 : row.pinned
  })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写商品名称')
    return
  }
  saving.value = true
  try {
    await saveSandboxShopItem({
      ...form,
      worldId: form.id ? undefined : selectedWorldId.value,
      icon: undefined,
      originalPrice: undefined,
      totalStock: undefined
    })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onToggleEnabled(row, value) {
  await saveSandboxShopItem({ id: row.id, name: row.name, enabled: value ? 1 : 0, price: row.price, rarity: row.rarity, stock: row.stock, pinned: row.pinned })
  row.enabled = value ? 1 : 0
}

async function onTogglePinned(row, value) {
  await saveSandboxShopItem({ id: row.id, name: row.name, pinned: value ? 1 : 0, enabled: row.enabled, price: row.price, rarity: row.rarity, stock: row.stock })
  row.pinned = value ? 1 : 0
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除商品「${row.name}」吗？`, '删除商品', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteSandboxShopItem(row.id)
  ElMessage.success('已删除')
  await load()
}

async function onSaveSettings() {
  savingSetting.value = true
  try {
    await saveSandboxSettings({ ...settings })
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
  await loadOrders()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.tip { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.range-sep { margin: 0 8px; color: var(--el-text-color-secondary); }
.tips { margin-bottom: 14px; }
.stat-row { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 6px; }
.mt { margin-top: 14px; }
.item-cell { display: flex; align-items: center; gap: 10px; }
.item-emoji { font-size: 22px; }
.item-name { font-weight: 600; }
.item-rarity { font-size: 11px; color: var(--el-text-color-secondary); margin-left: 6px; }
.item-desc { font-size: 12px; color: var(--el-text-color-secondary); }
.soldout { color: var(--el-color-danger); font-weight: 600; }
.setting-form { max-width: 760px; }
.collapse-title { font-weight: 600; }
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
