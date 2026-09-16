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

      <!-- 多世界：切换 / 新建 / 删除 / 两个开关（是否运行、前台是否可见） -->
      <div class="world-bar">
        <span class="world-label">当前世界</span>
        <el-select v-model="selectedWorldId" style="width: 220px" @change="onSwitchWorld">
          <el-option v-for="item in worlds" :key="item.id" :label="item.name || ('世界 ' + item.id)" :value="item.id">
            <span>{{ item.name || ('世界 ' + item.id) }}</span>
            <span class="world-tag">{{ item.enabled === 1 ? '运行中' : '已停止' }}</span>
            <span class="world-tag">{{ item.portalVisible === 1 ? '前台可见' : '前台隐藏' }}</span>
          </el-option>
        </el-select>
        <el-button type="primary" plain @click="onCreateWorld">新建世界</el-button>
        <el-button type="danger" plain :disabled="!selectedWorldId" @click="onDeleteWorld">删除世界</el-button>
        <el-divider direction="vertical" />
        <span class="world-label">是否运行</span>
        <el-switch :model-value="currentWorld.enabled === 1" @change="onToggleWorldEnabled" />
        <span class="tip">关闭后这个世界不再自动行动（角色与历史都保留）</span>
        <el-divider direction="vertical" />
        <span class="world-label">前台可见</span>
        <el-switch :model-value="currentWorld.portalVisible === 1" @change="onToggleWorldVisible" />
        <span class="tip">关闭后前台世界下拉里不再出现；开着但停跑时，前台只看历史</span>
      </div>

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
            <span class="tip">
              共 {{ locations.length }} 个地点；区域已覆盖地图 {{ coverage.toFixed(1) }}%；
              点击地图空白处可新增地点，拖动区域可整体移动
            </span>
          </div>
        </div>
      </template>

      <div
        ref="mapRef"
        class="map-preview"
        :class="{ drawing: editing }"
        @click="onMapClick"
        @pointermove="onMapHover"
        @pointerleave="onMapLeave"
      >
        <img v-if="world.mapImage" :src="world.mapImage" class="map-img" alt="地图背景" />
        <div v-else class="map-placeholder">还没有上传地图背景图，可以先用下方表格维护地点坐标</div>

        <!-- 区域形状层：viewBox 就是 0~100 的百分比坐标系 -->
        <svg class="edit-layer" viewBox="0 0 100 100" preserveAspectRatio="none">
          <!-- 冲突高亮：与其它区域交叉重叠的部分标红 -->
          <rect
            v-for="(cell, index) in conflictCells"
            :key="'cf-' + index"
            :x="cell[0]"
            :y="cell[1]"
            :width="cell[2]"
            :height="cell[3]"
            class="conflict-cell"
          />

          <!-- 已保存的地点：拖动可整体移动（正在编辑的那个淡化显示） -->
          <template v-for="loc in locations" :key="'loc-' + loc.id">
            <polygon
              v-if="polygonOf(loc)"
              :points="pointsOf(polygonOf(loc))"
              class="loc-shape"
              :class="{ dim: editing && form.id === loc.id }"
              @pointerdown.stop="onSavedDown($event, loc)"
            />
            <circle
              v-else
              :cx="Number(loc.x == null ? 50 : loc.x)"
              :cy="Number(loc.y == null ? 50 : loc.y)"
              r="2.2"
              class="loc-shape point"
              :class="{ dim: editing && form.id === loc.id }"
              @pointerdown.stop="onSavedDown($event, loc)"
            />
          </template>

          <!-- 正在描边的区域 -->
          <polygon
            v-if="workingPolygon.length >= 3"
            :points="pointsOf(workingPolygon)"
            class="working-poly"
            :class="{ bad: !!conflict }"
          />
          <polyline
            v-else-if="workingPolygon.length === 2"
            :points="pointsOf(workingPolygon)"
            class="working-line"
          />
          <line
            v-if="rubberBand"
            :x1="rubberBand.from[0]"
            :y1="rubberBand.from[1]"
            :x2="rubberBand.to[0]"
            :y2="rubberBand.to[1]"
            class="rubber-line"
          />
          <circle
            v-for="(point, index) in workingPolygon"
            :key="'vertex-' + index"
            :cx="point[0]"
            :cy="point[1]"
            r="1.2"
            class="vertex-dot"
            :title="'拖动调整第 ' + (index + 1) + ' 个顶点；双击结束描边'"
            @pointerdown.stop="onVertexDown($event, index)"
            @dblclick.stop="finishPolygon()"
          />
        </svg>

        <!-- 地名标签 -->
        <span
          v-for="loc in locations"
          :key="'label-' + loc.id"
          class="map-label"
          :class="{ dim: editing && form.id === loc.id }"
          :style="labelStyle(loc)"
        >
          <LocationIcon :icon="loc.icon" :size="13" />
          <span>{{ loc.name }}</span>
        </span>
      </div>

      <!-- 地点编辑表单：直接放在地图下方，不用弹窗 -->
      <div v-if="editing" class="loc-form">
        <div class="loc-form-head">
          <strong>{{ form.id ? '编辑地点' : '新增地点' }}</strong>
          <span class="tip">地图上的彩色轮廓就是它的区域，可以拖动整体移动、拖顶点微调</span>
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
            <el-form-item label="区域形状">
              <el-radio-group v-model="drawingMode" size="small">
                <el-radio-button label="polygon">描边区域</el-radio-button>
                <el-radio-button label="point">单点地点</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="drawingMode === 'polygon'" label="描边">
              <div class="draw-tools">
                <span class="vertex-count">当前 {{ workingPolygon.length }} 个顶点</span>
                <el-button size="small" :disabled="!workingPolygon.length" @click="undoVertex">撤销上一个点</el-button>
                <el-button size="small" :disabled="!workingPolygon.length" @click="clearPolygon">清空重画</el-button>
                <el-button size="small" :type="magicMode ? 'primary' : 'default'" @click="magicMode = !magicMode">
                  魔法棒{{ magicMode ? '（已开启）' : '' }}
                </el-button>
                <el-button size="small" :disabled="workingPolygon.length < 3" @click="finishPolygon">完成描边</el-button>
                <el-checkbox v-model="snapEnabled">顶点吸附</el-checkbox>
              </div>
              <div class="tip draw-tip">
                在地图上逐点点击描边，双击某个顶点或点「完成描边」收尾；
                开启「魔法棒」后点一下地图上颜色均匀的区域，会自动描出轮廓草稿（再手动拖顶点微调）；
                开启「顶点吸附」后顶点会自动贴到相邻区域的边或顶点上，方便画出既贴边又不重叠的相邻区域
              </div>
            </el-form-item>
            <el-form-item v-else label="坐标">
              <el-input-number v-model="form.x" :min="0" :max="100" controls-position="right" />
              <span class="range-sep">,</span>
              <el-input-number v-model="form.y" :min="0" :max="100" controls-position="right" />
              <span class="tip">单点地点（0~100 的地图百分比），适合传送门这类没有范围的去处</span>
            </el-form-item>
            <el-form-item v-if="conflict" label="重叠检查">
              <span class="conflict-text">
                与「{{ conflict.name }}」重叠了它面积的 {{ conflict.percent.toFixed(0) }}%（地图上标红的部分）；
                区域之间不能交叉重叠，可以贴着画，或者改成包含关系（比如国家里放城市）
              </span>
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
        <el-table-column label="区域范围" width="220">
          <template #default="{ row }">
            <span v-if="polygonOf(row)">多边形 · {{ polygonOf(row).length }} 个顶点</span>
            <span v-else>单点 ({{ row.x }}, {{ row.y }})</span>
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
        <el-form-item label="失败退避">
          <el-input v-model="settings.failBackoffBaseMinutes" style="width: 90px" />
          <span class="range-sep">~</span>
          <el-input v-model="settings.failBackoffMaxMinutes" style="width: 90px" />
          <span class="tip">
            分钟。AI 调用失败时把角色的下次行动时间往后推（连续失败按 2 倍递增，最多到上限），
            避免模型挂掉后每 5 分钟重试一次白烧额度；成功一次即清零，管理员「立即执行一次」不受影响
          </span>
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
        <el-form-item label="系统调用模型">
          <el-input
            v-model="settings.systemModel"
            style="width: 280px"
            placeholder="留空则用角色自己的模型"
            maxlength="100"
          />
          <span class="tip">
            定时行动、记忆总结等系统级调用回落到系统服务商时使用；请填系统服务商上真实存在的模型名
          </span>
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
        <el-form-item label="旅人低语">
          <el-switch v-model="settings.whisperEnabled" active-value="1" inactive-value="0" />
          <span class="tip">
            关闭后前台不再显示留言入口（整块隐藏），接口也会拦截；历史低语与已消耗的积分都保留，后台仍可查看
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
import { magicWandPolygon } from '@/utils/sandboxTrace'
import { useSandboxWorld } from '@/composables/useSandboxWorld'
import {
  REL_CROSS,
  containsPoint,
  coveragePercent,
  labelPoint,
  normalizePolygon,
  overlapAllowed,
  overlapCells,
  overlapRatio,
  parsePolygon,
  polygonOf,
  polygonToJson,
  relation,
  selfIntersects,
  snapPoint
} from '@/utils/sandboxGeo'
import {
  sandboxWorld,
  saveSandboxWorld,
  sandboxWorlds,
  deleteSandboxWorld,
  setSandboxWorldEnabled,
  setSandboxWorldVisible,
  sandboxLocations,
  saveSandboxLocation,
  deleteSandboxLocation,
  sandboxSettings,
  saveSandboxSettings
} from '@/api/sandbox'

const uploadHeaders = { Authorization: localStorage.getItem('token') || '' }

const world = reactive({
  id: null, name: '', description: '', mapImage: '', worldPrompt: '', enabled: 1, portalVisible: 1
})
/** 全部世界与当前选中的世界（三个沙盒页面共用同一个选择） */
const worlds = ref([])
const { currentWorldId, setCurrentWorld } = useSandboxWorld()
const selectedWorldId = ref(null)
const currentWorld = computed(() => worlds.value.find((item) => item.id === selectedWorldId.value) || {})
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
  aiIntervalMax: '720',
  systemModel: '',
  failBackoffBaseMinutes: '15',
  failBackoffMaxMinutes: '120',
  whisperEnabled: '1'
})

const loading = ref(false)
const saving = ref(false)
const savingWorld = ref(false)
const savingSetting = ref(false)
const editing = ref(false)
const mapRef = ref(null)
/** 地点表单：x,y 是区域标注点（单点地点就是它的坐标），区域形状走 workingPolygon */
const form = reactive({
  id: null,
  name: '',
  icon: 'pin',
  x: 44,
  y: 46,
  description: '',
  sortOrder: 0
})

const isCustomIcon = computed(() => /^https?:\/\//i.test(form.icon || '') || (form.icon || '').startsWith('/'))

/** 描边模式：polygon = 套索/魔法棒画区域，point = 单点地点（传送门这类） */
const drawingMode = ref('polygon')
/** 顶点吸附：拖点/加点时自动贴到已有区域的顶点或边上，方便画出无缝不重叠的相邻区域 */
const snapEnabled = ref(true)
/** 魔法棒模式：点击地图按颜色自动描出轮廓草稿 */
const magicMode = ref(false)
/** 正在描边的顶点（还没保存） */
const workingPolygon = ref([])
/** 与其它区域的重叠冲突：{ name, percent } */
const conflict = ref(null)
/** 冲突高亮的格子 */
const conflictCells = ref([])
/** 区域覆盖率（所有地点合起来覆盖地图的百分比） */
const coverage = ref(0)
/** 鼠标位置，用于描边时的橡皮筋预览 */
const cursorPoint = ref(null)

let drag = null
let moved = false
let suppressMapClick = false

function clampPct(value) {
  return Math.min(100, Math.max(0, Number(value) || 0))
}

/** 多边形顶点转 SVG points 字符串（viewBox 就是 0~100 的百分比坐标系） */
function pointsOf(polygon) {
  return (polygon || []).map(([x, y]) => `${x},${y}`).join(' ')
}

/** 地名标签位置：区域标注点（形心；凹多边形退回内部点） */
function labelStyle(loc) {
  const polygon = polygonOf(loc)
  const point = polygon
    ? labelPoint(polygon)
    : [Number(loc.x == null ? 50 : loc.x), Number(loc.y == null ? 50 : loc.y)]
  return { left: point[0] + '%', top: point[1] + '%' }
}

/** 描边时的橡皮筋：从最后一个顶点连到鼠标当前位置 */
const rubberBand = computed(() => {
  if (!editing.value || magicMode.value || drawingMode.value !== 'polygon') return null
  if (!workingPolygon.value.length || !cursorPoint.value) return null
  return {
    from: workingPolygon.value[workingPolygon.value.length - 1],
    to: [cursorPoint.value.x, cursorPoint.value.y]
  }
})

/** 顶点吸附：把点吸到相邻区域的顶点或边上（相邻区域才能既贴边又不重叠） */
function applySnap(point) {
  if (!snapEnabled.value) {
    return [point.x, point.y]
  }
  const hit = snapPoint([point.x, point.y], locations.value, { excludeId: form.id })
  return [hit.point[0], hit.point[1]]
}

/** 刷新区域覆盖率（所有地点合起来覆盖了地图多少） */
function refreshCoverage() {
  coverage.value = coveragePercent(locations.value)
}

let lastConflictCheck = 0

/** 拖动顶点时没必要每一帧都算，稍微节流一下 */
function recomputeConflictThrottled() {
  const now = Date.now()
  if (now - lastConflictCheck < 90) return
  lastConflictCheck = now
  recomputeConflict()
}

/**
 * 检查正在描边的区域是否与其它地点冲突。
 * 交叉重叠超过 1% 就标红并阻止保存；完全包含（嵌套，如国家里放城市）和轻微压边放行。
 */
function recomputeConflict() {
  conflict.value = null
  conflictCells.value = []
  if (drawingMode.value !== 'polygon') return
  const normalized = normalizePolygon(workingPolygon.value)
  if (normalized.length < 3) return
  let worst = null
  for (const other of locations.value) {
    if (form.id != null && other.id === form.id) continue
    const otherPolygon = polygonOf(other)
    if (!otherPolygon) {
      // 对方是单点地点：它落在我画的区域里也算冲突
      if (other.x != null && other.y != null && containsPoint(normalized, Number(other.x), Number(other.y))) {
        worst = { name: other.name, percent: 1, other: null, polygon: normalized }
        break
      }
      continue
    }
    if (relation(normalized, otherPolygon) !== REL_CROSS) continue
    // 容差按区域大小自适应：小区域 10 单位²，大区域按 3% 放宽（手绘压边允许，真重叠拦下）
    if (overlapAllowed(normalized, otherPolygon)) continue
    const percent = overlapRatio(normalized, otherPolygon)
    if (percent <= 0) continue
    if (!worst || percent > worst.percent) {
      worst = { name: other.name, percent, other: otherPolygon, polygon: normalized }
    }
  }
  if (worst) {
    conflict.value = worst
    conflictCells.value = worst.other ? overlapCells(worst.polygon, worst.other) : []
  }
}

async function loadAll() {
  loading.value = true
  try {
    // 先取世界列表，确定当前世界（优先用上次选的那个），再加载它名下的地图与地点
    worlds.value = (await sandboxWorlds()) || []
    if (!worlds.value.length) {
      // 一个世界都没有：给一个空表单，保存时会新建
      setCurrentWorld(null)
      selectedWorldId.value = null
      Object.assign(world, { id: null, name: '', description: '', mapImage: '', worldPrompt: '', enabled: 1, portalVisible: 1 })
      locations.value = []
      Object.assign(settings, (await sandboxSettings()) || {})
      return
    }
    const stored = currentWorldId.value
    const exists = worlds.value.some((item) => item.id === stored)
    setCurrentWorld(exists ? stored : worlds.value[0].id)
    selectedWorldId.value = currentWorldId.value

    const [w, locs, s] = await Promise.all([
      sandboxWorld(selectedWorldId.value),
      sandboxLocations(selectedWorldId.value),
      sandboxSettings()
    ])
    Object.assign(world, w || {})
    if (!world.name) world.name = ''
    locations.value = locs || []
    refreshCoverage()
    Object.assign(settings, s || {})
  } finally {
    loading.value = false
  }
}

async function loadLocations() {
  locations.value = await sandboxLocations(selectedWorldId.value)
  refreshCoverage()
}

/** 切换世界：三个沙盒页面共用这个选择 */
async function onSwitchWorld(id) {
  setCurrentWorld(id)
  cancelEdit()
  await loadAll()
}

/** 新建世界：只填名字，其余（地图、地点、角色）由管理员自己配 */
async function onCreateWorld() {
  let name = ''
  try {
    const res = await ElMessageBox.prompt('给新世界起个名字（之后可以在下方继续配置地图与世界观）', '新建世界', {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '名字不能为空'
    })
    name = res.value.trim()
  } catch (e) {
    return
  }
  await saveSandboxWorld({ name, description: '', enabled: 1, portalVisible: 1 })
  ElMessage.success('世界已创建，记得上传地图并添加地点')
  worlds.value = (await sandboxWorlds()) || []
  const created = worlds.value[worlds.value.length - 1]
  setCurrentWorld(created ? created.id : null)
  selectedWorldId.value = currentWorldId.value
  await loadAll()
}

/** 删除世界：连同它的角色、地点、行动等数据一起删掉（不可恢复） */
async function onDeleteWorld() {
  const target = currentWorld.value
  if (!target || !target.id) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除世界「${target.name || target.id}」吗？`
        + '它名下的角色、地点、行动记录、记忆、背包、好感度、纪闻、旅人低语与金币流水都会一起删除，且不可恢复。',
      '删除世界',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await deleteSandboxWorld(target.id)
  ElMessage.success('世界已删除')
  setCurrentWorld(null)
  await loadAll()
}

/** 切换这个世界的「是否运行」 */
async function onToggleWorldEnabled(val) {
  if (!selectedWorldId.value) return
  await setSandboxWorldEnabled(selectedWorldId.value, val ? 1 : 0)
  ElMessage.success(val ? '这个世界已开始自动行动' : '这个世界已停止自动行动（前台仍可查看历史）')
  await loadAll()
}

/** 切换这个世界的「前台是否可见」 */
async function onToggleWorldVisible(val) {
  if (!selectedWorldId.value) return
  await setSandboxWorldVisible(selectedWorldId.value, val ? 1 : 0)
  ElMessage.success(val ? '前台世界下拉里会显示这个世界' : '前台世界下拉里不再显示这个世界')
  await loadAll()
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
  if (!editing.value) {
    openAdd(point.x, point.y)
    return
  }
  if (drawingMode.value === 'point') {
    form.x = Math.round(point.x)
    form.y = Math.round(point.y)
    return
  }
  if (magicMode.value) {
    runMagicWand(point)
    return
  }
  workingPolygon.value = [...workingPolygon.value, applySnap(point)]
  recomputeConflict()
}

function onMapHover(event) {
  if (!editing.value || !world.mapImage) return
  cursorPoint.value = percentFromEvent(event)
}

function onMapLeave() {
  cursorPoint.value = null
}

function undoVertex() {
  workingPolygon.value = workingPolygon.value.slice(0, -1)
  recomputeConflict()
}

function clearPolygon() {
  workingPolygon.value = []
  conflict.value = null
  conflictCells.value = []
}

/**
 * 描边收尾：去掉重复/共线顶点，检查顶点数与自交，最后确认没有重叠冲突。
 * @returns {boolean} 是否可以保存
 */
function finishPolygon() {
  const cleaned = normalizePolygon(workingPolygon.value)
  if (cleaned.length < 3) {
    ElMessage.warning('至少需要 3 个顶点才能围出一块区域')
    return false
  }
  if (selfIntersects(cleaned)) {
    ElMessage.warning('区域边界不能自交（不能画成 8 字形），请调整顶点')
    return false
  }
  workingPolygon.value = cleaned
  recomputeConflict()
  if (conflict.value) {
    ElMessage.warning(
      `与「${conflict.value.name}」重叠了 ${conflict.value.percent.toFixed(1)}%，区域之间不能交叉重叠`
    )
    return false
  }
  return true
}

/** 魔法棒：点一下地图上颜色均匀的区域，自动描出轮廓草稿 */
function runMagicWand(point) {
  const image = mapRef.value ? mapRef.value.querySelector('.map-img') : null
  const result = magicWandPolygon(image, point.x, point.y)
  if (!result.ok) {
    ElMessage.warning(result.message)
    return
  }
  const cleaned = normalizePolygon(result.polygon)
  if (cleaned.length < 3) {
    ElMessage.warning('没描出有效轮廓，换个位置点点看')
    return
  }
  workingPolygon.value = cleaned
  recomputeConflict()
  if (conflict.value) {
    ElMessage.warning(
      `自动描出的区域与「${conflict.value.name}」重叠了 ${conflict.value.percent.toFixed(1)}%，请拖顶点调整`
    )
  } else {
    ElMessage.success(`已描出 ${cleaned.length} 个顶点的轮廓草稿，可以拖动顶点微调`)
  }
}

/** 新增地点：以点击位置作为第一个顶点，接着继续点就能描边 */
function openAdd(cx = 50, cy = 50) {
  form.id = null
  form.name = ''
  form.icon = 'pin'
  form.x = Math.round(clampPct(cx))
  form.y = Math.round(clampPct(cy))
  form.description = ''
  form.sortOrder = locations.value.length
  drawingMode.value = 'polygon'
  magicMode.value = false
  workingPolygon.value = [[form.x, form.y]]
  conflict.value = null
  conflictCells.value = []
  editing.value = true
}

function openEdit(row) {
  form.id = row.id
  form.name = row.name
  form.icon = row.icon || 'pin'
  form.x = row.x == null ? 50 : row.x
  form.y = row.y == null ? 50 : row.y
  form.description = row.description || ''
  form.sortOrder = row.sortOrder || 0
  const polygon = parsePolygon(row.polygon)
  if (polygon.length >= 3) {
    drawingMode.value = 'polygon'
    workingPolygon.value = polygon.map((p) => [...p])
  } else if (Number(row.width || 0) > 0 && Number(row.height || 0) > 0) {
    // 老的矩形地点：自动转成 4 个顶点，可以直接拖成多边形
    drawingMode.value = 'polygon'
    workingPolygon.value = (polygonOf(row) || []).map((p) => [...p])
  } else {
    drawingMode.value = 'point'
    workingPolygon.value = []
  }
  magicMode.value = false
  conflict.value = null
  conflictCells.value = []
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  workingPolygon.value = []
  conflict.value = null
  conflictCells.value = []
  magicMode.value = false
  drag = null
}

async function onSaveLocation() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入地点名称')
    return
  }
  if (drawingMode.value === 'polygon' && !finishPolygon()) {
    return
  }
  saving.value = true
  try {
    const payload = {
      id: form.id,
      name: form.name,
      icon: form.icon,
      description: form.description,
      sortOrder: form.sortOrder,
      // 新建地点要指明属于哪个世界；编辑时后端已经有记录，不必再传
      worldId: form.id ? undefined : selectedWorldId.value
    }
    if (drawingMode.value === 'polygon') {
      // 多边形区域：宽高由后端按外接矩形自动算
      payload.polygon = polygonToJson(workingPolygon.value)
    } else {
      // 单点地点：传空字符串表示清掉多边形，按坐标点判定
      payload.polygon = ''
      payload.x = Math.round(clampPct(form.x))
      payload.y = Math.round(clampPct(form.y))
      payload.width = 0
      payload.height = 0
    }
    await saveSandboxLocation(payload)
    ElMessage.success('地点已保存')
    editing.value = false
    workingPolygon.value = []
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

/** 拖动「正在编辑的区域」的某个顶点来微调边界 */
function onVertexDown(event, index) {
  event.preventDefault()
  drag = { mode: 'vertex', index }
  moved = false
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

/**
 * 拖动已保存的区域 = 整体平移（松手后自动保存）。
 * 正在编辑别的区域时忽略，避免误操作；单击不拖动则是打开编辑。
 */
function onSavedDown(event, loc) {
  if (editing.value && form.id !== loc.id) return
  event.preventDefault()
  const polygon = polygonOf(loc) || [[Number(loc.x == null ? 50 : loc.x), Number(loc.y == null ? 50 : loc.y)]]
  drag = {
    mode: 'translate',
    loc,
    // 单点地点整体平移时改的是坐标，不是多边形（后端要求多边形至少 3 个顶点）
    isPolygon: !!polygonOf(loc),
    polygon: polygon.map((p) => [...p]),
    start: percentFromEvent(event)
  }
  moved = false
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerMove(event) {
  if (!drag) return
  const point = percentFromEvent(event)
  if (drag.mode === 'vertex') {
    const snapped = applySnap(point)
    const next = workingPolygon.value.map((p) => [...p])
    next[drag.index] = snapped
    workingPolygon.value = next
    moved = true
    recomputeConflictThrottled()
    return
  }
  if (drag.mode === 'translate') {
    const dx = point.x - drag.start.x
    const dy = point.y - drag.start.y
    if (Math.abs(dx) > 0.4 || Math.abs(dy) > 0.4) {
      moved = true
    }
    const shifted = drag.polygon.map(([x, y]) => [clampPct(x + dx), clampPct(y + dy)])
    drag.shifted = shifted
    if (!drag.isPolygon) {
      // 单点地点：直接改坐标做实时预览
      drag.loc.x = Math.round(shifted[0][0])
      drag.loc.y = Math.round(shifted[0][1])
    } else if (editing.value && form.id === drag.loc.id) {
      // 正在编辑的就是它 → 只改预览，保存时一起提交
      workingPolygon.value = shifted
      recomputeConflictThrottled()
    } else {
      // 其它已保存区域：直接改本地对象做实时预览，松手后再落库
      drag.loc.polygon = polygonToJson(shifted)
    }
  }
}

async function onPointerUp() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  const state = drag
  drag = null
  if (!state) return
  cursorPoint.value = null
  suppressMapClick = true
  setTimeout(() => {
    suppressMapClick = false
  }, 200)
  if (state.mode === 'vertex') {
    recomputeConflict()
    return
  }
  // 平移：没真正移动就是一次单击 → 打开编辑
  if (!moved) {
    openEdit(state.loc)
    return
  }
  if (editing.value && form.id === state.loc.id) {
    recomputeConflict()
    return
  }
  try {
    const shifted = state.shifted || state.polygon
    const payload = state.isPolygon
      ? { ...state.loc, polygon: polygonToJson(shifted) }
      : {
          ...state.loc,
          polygon: '',
          x: Math.round(shifted[0][0]),
          y: Math.round(shifted[0][1]),
          width: 0,
          height: 0
        }
    await saveSandboxLocation(payload)
    ElMessage.success('区域已移动')
  } catch (e) {
    // 保存失败（例如移动后与别的区域重叠）时回滚显示；
    // 后端返回的原因由请求层统一弹出提示，这里不重复提示
  } finally {
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
/* 多世界：切换与开关工具条 */
.world-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
}
.world-label { font-size: 13px; color: var(--el-text-color-regular); }
.world-tag {
  margin-left: 8px;
  padding: 0 6px;
  border-radius: 999px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}
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
/* 区域编辑层：viewBox 就是 0~100 的百分比坐标系，SVG 坐标可以直接当地图坐标 */
.edit-layer {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 3;
  overflow: visible;
}
.loc-shape {
  fill: rgba(64, 158, 255, 0.16);
  stroke: var(--el-color-primary);
  stroke-width: 1.5;
  stroke-dasharray: 4 3;
  /* 地图被拉伸时线宽保持不变 */
  vector-effect: non-scaling-stroke;
  cursor: move;
  transition: fill 0.2s ease;
}
.loc-shape:hover { fill: rgba(64, 158, 255, 0.3); }
.loc-shape.dim { opacity: 0.3; }
.loc-shape.point { fill: rgba(64, 158, 255, 0.5); }

/* 正在描边的区域 */
.working-poly {
  fill: rgba(255, 111, 159, 0.2);
  stroke: #ff6f9f;
  stroke-width: 1.8;
  vector-effect: non-scaling-stroke;
  pointer-events: none;
}
.working-poly.bad {
  fill: rgba(245, 108, 108, 0.26);
  stroke: #f56c6c;
}
.working-line {
  fill: none;
  stroke: #ff6f9f;
  stroke-width: 1.8;
  vector-effect: non-scaling-stroke;
  pointer-events: none;
}
.rubber-line {
  stroke: #ff6f9f;
  stroke-width: 1.2;
  stroke-dasharray: 3 3;
  vector-effect: non-scaling-stroke;
  pointer-events: none;
}
.vertex-dot {
  fill: #fff;
  stroke: #ff6f9f;
  stroke-width: 1.4;
  vector-effect: non-scaling-stroke;
  cursor: grab;
}
.vertex-dot:hover { fill: #ff6f9f; }
.conflict-cell {
  fill: rgba(245, 108, 108, 0.35);
  stroke: none;
  pointer-events: none;
}

/* 地名标签：跟着区域标注点走，不占区域面积 */
.map-label {
  position: absolute;
  z-index: 4;
  transform: translate(-50%, -50%);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--el-text-color-primary);
  background: rgba(255, 255, 255, 0.88);
  white-space: nowrap;
  pointer-events: none;
}
.map-label svg { color: var(--el-color-primary); flex-shrink: 0; }
.map-label.dim { opacity: 0.4; }

/* 描边工具条 */
.draw-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.vertex-count { font-size: 12px; color: var(--el-text-color-regular); }
.draw-tip { display: block; margin-top: 8px; line-height: 1.7; max-width: 760px; }
.conflict-text { color: var(--el-color-danger); font-size: 12px; line-height: 1.7; }

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
