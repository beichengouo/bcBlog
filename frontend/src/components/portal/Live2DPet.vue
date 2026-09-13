<template>
  <div v-if="globalEnabled" class="live2d-pet">
    <!-- 收起后的小按钮，方便随时重新打开看板娘 -->
    <button
      v-if="hidden"
      class="pet-control pet-control-single"
      @click="showPet"
      title="打开看板娘"
      aria-label="打开看板娘"
    >
      <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
        <path d="M12 3a4.5 4.5 0 0 1 3 7.9V14a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2v-3.1A4.5 4.5 0 0 1 12 3z" />
        <path d="M9 17h6M10 20h4" stroke="currentColor" stroke-width="1.6" fill="none" />
      </svg>
    </button>

    <!-- 右下角悬浮控制栏 -->
    <div v-if="!hidden" class="pet-toolbar">
      <button
        class="pet-control"
        :class="{ active: panelVisible }"
        @click="panelVisible = !panelVisible"
        title="切换看板娘"
        aria-label="切换看板娘"
      >
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8">
          <circle cx="8" cy="12" r="4" />
          <circle cx="16" cy="9" r="3" />
          <path d="M4.5 16.5c1.4 1.2 3.3 2 5.5 2s4.1-.8 5.5-2M14 7.8l5.5-2.2 1.3 3.2-4.4 1.8" />
        </svg>
      </button>
      <button class="pet-control" @click="hidePet" title="隐藏看板娘" aria-label="隐藏看板娘">
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M4 4l16 16M9 9a4 4 0 0 0 6 6M15.4 7.6A8.8 8.8 0 0 0 12 7c-4.4 0-8 3.5-9 5 .3.4 1.5 2 3.6 3.3M8.1 5.6A9.7 9.7 0 0 1 12 5c4.4 0 8 3.5 9 5-.4.6-1.2 1.5-2.4 2.4" />
        </svg>
      </button>
    </div>

    <!-- 模型选择面板：只展示启用中的模型，并标注当前使用中的模型 -->
    <transition name="pet-panel">
      <div v-if="panelVisible && !hidden" class="pet-panel">
        <div class="pet-panel-title">选择看板娘</div>
        <button
          v-for="m in models"
          :key="m.id"
          class="pet-model-item"
          :class="{ active: m.id === currentId }"
          @click="switchTo(m.id)"
        >
          <span class="pet-model-name">{{ m.name }}</span>
          <span class="pet-model-desc">{{ m.description }}</span>
          <span v-if="m.id === currentId" class="pet-model-check">✓</span>
        </button>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { getActiveLive2dModel, getPortalLive2dModels } from '@/api/live2d'
import { getPortalConfig } from '@/api/config'
import { useThemeStore } from '@/store/theme'

const themeStore = useThemeStore()

// 模型列表、当前模型 ID 与组件显示状态
const models = ref([])
const currentId = ref(null)
const panelVisible = ref(false)
const hidden = ref(false)
const globalEnabled = ref(true)
let widget = null

/**
 * 根据当前模型列表创建 Live2D 挂件。
 * 传入模型数组后，l2d-widget 会自带一个切换按钮，但我们额外提供带中文标注的切换面板。
 */
async function buildWidget() {
  if (widget) {
    try {
      await widget.destroy()
    } catch (e) {
      // 忽略销毁阶段的异常，避免阻塞后续重建
    }
    widget = null
  }

  let list = models.value
  // 列表为空时回退到前台接口返回的当前模型
  if (!list || !list.length) {
    try {
      const active = await getActiveLive2dModel()
      if (active) {
        list = [active]
        models.value = list
      }
    } catch (e) {
      // 前台接口失败时使用内置蕾姆兜底
      list = [
        {
          id: 0,
          name: '蕾姆（默认）',
          description: '蓝色短发女仆，经典人气角色',
          url: 'https://model.hacxy.cn/rem/model.json'
        }
      ]
      models.value = list
    }
  }

  if (!list.length) {
    return
  }

  // 主题色跟随白天樱粉 / 夜晚星空主题
  const primaryColor = themeStore.isDark ? 'rgba(99, 102, 241, 0.9)' : 'rgba(244, 114, 182, 0.9)'

  // 动态导入 l2d-widget，避免影响首屏主包
  const { createWidget } = await import('l2d-widget')
  widget = createWidget({
    model: list.map((m) => ({ path: m.url })),
    position: 'bottom-right',
    size: { width: 280, height: 300 },
    primaryColor,
    transitionType: 'fade',
    transitionDuration: 900,
    tips: {
      welcomeMessage: ['欢迎来到 bcBlog～', '今天也一起逛逛吧！'],
      messages: ['休息一下，喝杯茶吧～', '要不要看看最新的文章呢？'],
      duration: 3500,
      interval: 9000,
      typing: { speed: 90 }
    }
  })

  // 初始定位到当前展示的模型
  if (currentId.value != null) {
    const idx = list.findIndex((m) => m.id === currentId.value)
    if (idx > 0) {
      try {
        await widget.switchModel(idx)
      } catch (e) {
        // 单个模型时没有可切换目标，忽略即可
      }
    }
  }
}

/** 切换到指定模型。 */
async function switchTo(id) {
  if (!widget || id === currentId.value) {
    panelVisible.value = false
    return
  }
  const idx = models.value.findIndex((m) => m.id === id)
  if (idx < 0) {
    return
  }
  await widget.switchModel(idx)
  currentId.value = id
  panelVisible.value = false
}

/** 隐藏看板娘。 */
function hidePet() {
  hidden.value = true
  panelVisible.value = false
  if (widget) {
    widget.sleep()
  }
}

/** 重新显示看板娘。 */
async function showPet() {
  hidden.value = false
  // sleep 后点击状态条即可唤醒，这里保险起见直接重建
  await buildWidget()
}

// 主题切换时重建挂件，让 UI 颜色跟随樱粉 / 星空
watch(
  () => themeStore.isDark,
  () => {
    if (globalEnabled.value && !hidden.value) {
      buildWidget()
    }
  }
)

onMounted(async () => {
  // 后台关闭看板娘时，前台不创建任何看板娘 UI
  try {
    const config = await getPortalConfig()
    if (config.live2dEnabled === 0) {
      globalEnabled.value = false
      return
    }
  } catch (e) {
    // 配置读取失败时默认允许显示
  }

  // 优先拉取完整模型列表，切换面板才能展示带中文标注的全部角色
  try {
    const list = await getPortalLive2dModels()
    if (list && list.length) {
      models.value = list
      const active = list.find((m) => m.active === 1)
      if (active) {
        currentId.value = active.id
      }
    }
  } catch (e) {
    // 列表接口失败时，退而求其次只取当前启用的模型
    try {
      const active = await getActiveLive2dModel()
      if (active) {
        models.value = [active]
        currentId.value = active.id
      }
    } catch (e2) {
      // 都失败时 buildWidget 内部还会用蕾姆兜底
    }
  }
  await buildWidget()
})

onUnmounted(async () => {
  if (widget) {
    try {
      await widget.destroy()
    } catch (e) {
      // 忽略卸载阶段的异常
    }
    widget = null
  }
})
</script>

<style scoped>
.live2d-pet {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 90;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.pet-toolbar {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pet-control {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: var(--glass-bg);
  color: var(--text);
  cursor: pointer;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  transition: transform 0.2s ease, background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
  box-shadow: var(--shadow);
}
.pet-control:hover,
.pet-control.active {
  color: var(--accent);
  border-color: var(--accent);
  background: var(--accent-soft);
  transform: scale(1.05);
}
.pet-control-single {
  width: 42px;
  height: 42px;
}
.pet-panel {
  width: 240px;
  max-height: 52vh;
  overflow-y: auto;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--glass-bg);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  box-shadow: var(--shadow);
}
.pet-panel-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-strong);
  padding: 4px 4px 8px;
}
.pet-model-item {
  position: relative;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 9px 34px 9px 10px;
  margin-bottom: 6px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  text-align: left;
  transition: background 0.2s ease, border-color 0.2s ease;
}
.pet-model-item:last-child {
  margin-bottom: 0;
}
.pet-model-item:hover {
  background: var(--accent-soft);
}
.pet-model-item.active {
  border-color: var(--accent);
  background: var(--accent-soft);
}
.pet-model-name {
  font-size: 14px;
  font-weight: 600;
}
.pet-model-desc {
  font-size: 12px;
  color: var(--text-muted);
}
.pet-model-check {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--accent);
  font-weight: 800;
}
.pet-panel-enter-active,
.pet-panel-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.pet-panel-enter-from,
.pet-panel-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>

<style>
/* l2d-widget 挂载在 body 上，这里用全局样式做移动端适配 */
@media (max-width: 640px) {
  .live2d-pet {
    right: 8px;
    bottom: 8px;
  }
  .pet-panel {
    width: 210px;
  }
}
</style>
