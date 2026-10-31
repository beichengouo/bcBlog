import { waitForBootAssets } from './utils/bootAssets'
import { createApp, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { useThemeStore } from './store/theme'
import './styles/theme.css'

const app = createApp(App)

app.use(createPinia())
useThemeStore().apply()
app.use(router)
app.use(ElementPlus)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')

router.isReady().then(async () => {
  await nextTick()
  const result = await waitForBootAssets({ onProgress: progress => window.__irisBoot?.progress(progress) })
  if (result.failed || result.timedOut) window.__irisBoot?.partial()
  else window.__irisBoot?.ready()
}).catch(() => window.__irisBoot?.fail())
