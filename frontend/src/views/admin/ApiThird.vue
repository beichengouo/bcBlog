<template>
  <div class="api-third">
    <el-card class="block">
      <template #header>
        <div class="head">
          <span>第三方接口</span>
          <el-button type="primary" :loading="saving" @click="onSave">保存配置</el-button>
        </div>
      </template>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="这里统一管理前台使用到的免费第三方接口；接口异常时前台会自动降级，不影响网站主流程。"
        class="tip"
      />
    </el-card>

    <!-- 天气接口：UAPIS -->
    <el-card class="block">
      <template #header>
        <div class="head">
          <span>天气接口（UAPIS）</span>
          <el-tag type="success">免 Key</el-tag>
        </div>
      </template>
      <el-form label-width="90px" @submit.prevent>
        <el-form-item label="默认城市">
          <div class="inline">
            <el-input
              v-model="form.weatherCity"
              placeholder="如：北京；前台展示时会优先按访问 IP 自动定位"
              maxlength="50"
              class="grow"
            />
            <el-button :loading="weatherLoading" @click="testWeather">测试天气</el-button>
          </div>
        </el-form-item>
      </el-form>
      <div v-if="weatherResult" class="result">
        <span class="result-label">接口返回：</span>
        <span>{{ weatherResult }}</span>
      </div>
    </el-card>

    <!-- 一言接口：Hitokoto -->
    <el-card class="block">
      <template #header>
        <div class="head">
          <span>一言接口（Hitokoto）</span>
          <el-tag type="success">免 Key</el-tag>
        </div>
      </template>
      <el-form label-width="90px" @submit.prevent>
        <el-form-item label="分类缩写">
          <div class="inline">
            <el-input
              v-model="form.hitokotoCategories"
              placeholder="英文逗号分隔，如：d,i,k"
              maxlength="100"
              class="grow"
            />
            <el-button :loading="hitokotoLoading" @click="testHitokoto">测试一言</el-button>
          </div>
        </el-form-item>
      </el-form>
      <div v-if="hitokotoResult" class="result">
        <span class="result-label">接口返回：</span>
        <span>{{ hitokotoResult }}</span>
      </div>
    </el-card>

    <!-- 敏感词检测接口：UAPIS -->
    <el-card class="block">
      <template #header>
        <div class="head">
          <span>敏感词检测接口（UAPIS）</span>
          <el-tag type="success">免 Key</el-tag>
        </div>
      </template>
      <el-input
        v-model="sensitiveText"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="输入要测试的评论内容"
      />
      <div class="inline mt12">
        <el-button :loading="sensitiveLoading" @click="testSensitive">检测测试</el-button>
      </div>
      <div v-if="sensitiveResult" class="result">
        <span class="result-label">接口返回：</span>
        <span>{{ sensitiveResult }}</span>
      </div>
      <p class="muted">自定义敏感词列表目前仍从后端 application.yml 的 bcblog.sensitive-words 读取，默认词库已内置。</p>
    </el-card>

    <!-- IP 定位接口：百度地图 -->
    <el-card class="block">
      <template #header>
        <div class="head">
          <span>IP 定位接口（百度地图）</span>
          <el-tag type="warning">需 AK</el-tag>
        </div>
      </template>
      <el-form label-width="90px" @submit.prevent>
        <el-form-item label="API Key">
          <div class="inline">
            <el-input
              v-model="ipAk"
              type="password"
              show-password
              placeholder="填写百度地图普通 IP 定位 AK"
              maxlength="200"
              class="grow"
            />
            <el-button type="primary" :loading="ipAkSaving" @click="onSaveIpAk">保存 AK</el-button>
          </div>
        </el-form-item>
        <el-form-item label="测试 IP">
          <div class="inline">
            <el-input v-model="ipTestIp" placeholder="留空则查询本机出口 IP" maxlength="50" class="grow" />
            <el-button :loading="ipTestLoading" @click="onTestIp">查询位置</el-button>
          </div>
        </el-form-item>
      </el-form>
      <div v-if="ipTestResult" class="result">
        <span class="result-label">接口返回：</span>
        <span>{{ ipTestResult }}</span>
      </div>
      <p class="muted">登录日志页面中的 IP 位置查询会读取这里保存的 AK，并且需要手动点击，避免调用量过大。</p>
    </el-card>

    <!-- ACG 随机封面接口：ALAPI -->
    <el-card class="block">
      <template #header>
        <div class="head">
          <span>ACG 随机封面接口（ALAPI）</span>
          <el-tag type="warning">需 Token</el-tag>
        </div>
      </template>
      <el-form label-width="90px" @submit.prevent>
        <el-form-item label="Token">
          <div class="inline">
            <el-input
              v-model="acgToken"
              type="password"
              show-password
              placeholder="填写 ACG 随机封面 Token"
              maxlength="200"
              class="grow"
            />
            <el-button type="primary" :loading="acgTokenSaving" @click="onSaveAcgToken">保存 Token</el-button>
          </div>
        </el-form-item>
      </el-form>
      <p class="muted">文章新增页面的“随机封面”按钮会读取这里保存的 Token，用于快速获取一张 ACG 图片作为封面。</p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getConfig, saveConfig, getIpLocationAk, saveIpLocationAk, getAcgCoverToken, saveAcgCoverToken } from '@/api/config'
import { queryIpLocation } from '@/api/log'

const saving = ref(false)
const weatherLoading = ref(false)
const hitokotoLoading = ref(false)
const sensitiveLoading = ref(false)
const ipAkSaving = ref(false)
const ipTestLoading = ref(false)
const acgTokenSaving = ref(false)

const weatherResult = ref('')
const hitokotoResult = ref('')
const sensitiveResult = ref('')
const sensitiveText = ref('')
const ipAk = ref('')
const ipTestIp = ref('')
const ipTestResult = ref('')
const acgToken = ref('')

const form = reactive({
  weatherCity: '北京',
  hitokotoCategories: 'd,i,k'
})

// 保存第三方配置时，要把站点设置里的其他字段原样带回去，避免覆盖成空
let fullConfig = {}

async function load() {
  fullConfig = await getConfig()
  form.weatherCity = fullConfig.weatherCity || '北京'
  form.hitokotoCategories = fullConfig.hitokotoCategories || 'd,i,k'
  try {
    ipAk.value = (await getIpLocationAk()) || ''
  } catch (e) {
    // 读取 AK 失败时保持为空，不影响天气/一言配置加载
  }
  try {
    acgToken.value = (await getAcgCoverToken()) || ''
  } catch (e) {
    // 读取 Token 失败时保持为空
  }
}

async function onSave() {
  // 防止页面刚打开、完整配置尚未加载时只保存第三方字段，把其他站点设置覆盖为空
  if (!fullConfig || Object.keys(fullConfig).length === 0) {
    await load()
  }
  saving.value = true
  try {
    await saveConfig({
      ...fullConfig,
      weatherCity: form.weatherCity.trim(),
      hitokotoCategories: form.hitokotoCategories.trim()
    })
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

/** 测试 UAPIS 天气接口。 */
async function testWeather() {
  if (!form.weatherCity.trim()) {
    ElMessage.warning('请先填写默认城市')
    return
  }
  weatherLoading.value = true
  weatherResult.value = ''
  try {
    const res = await fetch(`https://uapis.cn/api/v1/misc/weather?city=${encodeURIComponent(form.weatherCity.trim())}`)
    const data = await res.json()
    if (!data || !data.weather) {
      weatherResult.value = '未获取到天气数据'
      return
    }
    weatherResult.value = `${data.weather}，${Math.round(data.temperature)}°C，湿度 ${data.humidity ?? '--'}%，${data.wind_direction || ''} ${data.wind_power || ''}`.trim()
  } catch (e) {
    weatherResult.value = '天气接口调用失败'
  } finally {
    weatherLoading.value = false
  }
}

/** 测试一言接口。 */
async function testHitokoto() {
  hitokotoLoading.value = true
  hitokotoResult.value = ''
  try {
    const query = form.hitokotoCategories
      .split(',')
      .map((c) => c.trim())
      .filter(Boolean)
      .map((c) => `c=${c}`)
      .join('&')
    const res = await fetch(`https://v1.hitokoto.cn/?${query}`)
    const data = await res.json()
    hitokotoResult.value = `${data.hitokoto || ''} —— ${data.from_who ? `${data.from} · ${data.from_who}` : data.from || ''}`.trim()
  } catch (e) {
    hitokotoResult.value = '一言接口调用失败'
  } finally {
    hitokotoLoading.value = false
  }
}

/** 测试 UAPIS 敏感词检测接口。 */
async function testSensitive() {
  if (!sensitiveText.value.trim()) {
    ElMessage.warning('请输入要测试的内容')
    return
  }
  sensitiveLoading.value = true
  sensitiveResult.value = ''
  try {
    const res = await fetch('https://uapis.cn/api/v1/text/profanitycheck', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: sensitiveText.value.trim() })
    })
    const data = await res.json()
    if (data && data.status === 'forbidden') {
      sensitiveResult.value = `命中敏感词，脱敏后：${data.masked_text || sensitiveText.value}`
    } else {
      sensitiveResult.value = '未命中敏感词'
    }
  } catch (e) {
    sensitiveResult.value = '敏感词检测接口调用失败'
  } finally {
    sensitiveLoading.value = false
  }
}

async function onSaveIpAk() {
  if (!ipAk.value.trim()) {
    ElMessage.warning('请先填写 AK')
    return
  }
  ipAkSaving.value = true
  try {
    await saveIpLocationAk(ipAk.value.trim())
    ElMessage.success('AK 已保存')
  } finally {
    ipAkSaving.value = false
  }
}

async function onTestIp() {
  ipTestLoading.value = true
  ipTestResult.value = ''
  try {
    ipTestResult.value = await queryIpLocation(ipTestIp.value.trim() || '')
  } catch (e) {
    ipTestResult.value = '查询失败'
  } finally {
    ipTestLoading.value = false
  }
}

async function onSaveAcgToken() {
  if (!acgToken.value.trim()) {
    ElMessage.warning('请先填写 Token')
    return
  }
  acgTokenSaving.value = true
  try {
    await saveAcgCoverToken(acgToken.value.trim())
    ElMessage.success('ACG 封面 Token 已保存')
  } finally {
    acgTokenSaving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.api-third {
  max-width: 720px;
}
.block {
  margin-bottom: 16px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.tip {
  margin: 0;
}
.inline {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  flex-wrap: wrap;
}
.grow {
  flex: 1;
  min-width: 220px;
}
.mt12 {
  margin-top: 12px;
}
.result {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--accent-soft);
  color: var(--text);
  font-size: 13px;
  line-height: 1.6;
}
.result-label {
  color: var(--text-muted);
}
.muted {
  margin: 12px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
