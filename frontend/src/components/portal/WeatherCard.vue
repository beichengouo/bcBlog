<template>
  <!-- 隐藏后只在原来的位置留一个小按钮，点一下就能收回来（选择记在本地） -->
  <button
    v-if="weather && hidden"
    class="weather-show"
    title="显示天气"
    aria-label="显示天气"
    @click="toggleHidden(false)"
  >☁</button>
  <div v-else-if="weather" class="weather-card glass">
    <div class="weather-head">
      <span class="weather-city">{{ city }}</span>
      <span class="weather-actions">
        <button class="weather-refresh" @click="load" title="刷新天气">↻</button>
        <button class="weather-hide" @click="toggleHidden(true)" title="隐藏天气卡片" aria-label="隐藏天气卡片">×</button>
      </span>
    </div>
    <div class="weather-main">
      <span class="weather-temp">{{ weather.temp }}°</span>
      <span class="weather-desc">{{ weather.desc }}</span>
    </div>
    <div class="weather-meta">
      <span>湿度 {{ weather.humidity }}%</span>
      <span>{{ weather.wind }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getPortalConfig } from '@/api/config'

const city = ref('北京')
// 访客可以把天气卡片收起来，选择记在本地（换设备/清缓存后重新出现）
const hidden = ref(localStorage.getItem('portalWeatherHidden') === '1')

function toggleHidden(value) {
  hidden.value = value
  try { localStorage.setItem('portalWeatherHidden', value ? '1' : '0') } catch (e) { /* 隐私模式下忽略 */ }
}
const weather = ref(null)

/** UAPIS：查询当前登录 IP 的位置信息，失败时返回空。 */
async function locateByIp() {
  try {
    const res = await fetch('https://uapis.cn/api/v1/network/myip')
    const data = await res.json()
    return data
  } catch (e) {
    return null
  }
}

async function load() {
  try {
    // 1. 先调用 UAPIS 位置查询，获取当前 IP 归属地
    const ipInfo = await locateByIp()
    if (ipInfo?.region) {
      // region 形如“中国 浙江 舟山”，取最后一段作为城市名
      const parts = ipInfo.region.split(/\s+/).filter(Boolean)
      const cityName = parts[parts.length - 1] || ''
      if (cityName) {
        city.value = cityName
      }
    }

    // 2. 再调用 UAPIS 天气接口，传入城市；接口也支持按 IP 自动定位
    const res = await fetch(`https://uapis.cn/api/v1/misc/weather?city=${encodeURIComponent(city.value)}`)
    const data = await res.json()
    if (!data || !data.weather) return
    weather.value = {
      temp: Math.round(data.temperature),
      desc: data.weather,
      wind: `${data.wind_direction || ''} ${data.wind_power || ''}`.trim(),
      humidity: data.humidity ?? '--'
    }
  } catch (e) {
    weather.value = null
  }
}

onMounted(async () => {
  try {
    const config = await getPortalConfig()
    if (config.weatherCity) city.value = config.weatherCity
  } catch (e) {
    // 使用默认城市
  }
  load()
})
</script>

<style scoped>.weather-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.weather-hide {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 16px;
  line-height: 1;
  padding: 0 2px;
  cursor: pointer;
  transition: color 0.2s ease;
}
.weather-hide:hover {
  color: var(--accent);
}
/* 收起后的小圆按钮：贴在原位置，点一下把卡片叫回来 */
.weather-show {
  position: fixed;
  left: 18px;
  top: calc(var(--header-height) + 22px);
  z-index: 40;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: var(--glass-bg);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  color: var(--text);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  box-shadow: var(--shadow);
  transition: color 0.2s ease, border-color 0.2s ease;
}
.weather-show:hover {
  color: var(--accent);
  border-color: var(--accent);
}
.weather-card {
  position: fixed;
  left: 18px;
  top: calc(var(--header-height) + 22px);
  z-index: 40;
  width: 168px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--glass-bg);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  box-shadow: var(--shadow);
}
.weather-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.weather-city {
  font-weight: 700;
  color: var(--text-strong);
}
.weather-refresh {
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 16px;
}
.weather-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 6px 0;
}
.weather-temp {
  font-size: 30px;
  font-weight: 800;
  color: var(--text-strong);
}
.weather-desc {
  color: var(--text-muted);
  font-size: 13px;
}
.weather-meta {
  display: flex;
  gap: 10px;
  color: var(--text-muted);
  font-size: 12px;
}
@media (max-width: 768px) {
  .weather-card {
    left: 12px;
    top: 76px;
    width: 150px;
  }
}
</style>
