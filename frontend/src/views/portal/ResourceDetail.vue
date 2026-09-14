<template>
  <div class="resource-detail" v-loading="loading">
    <template v-if="resource">
      <h1 class="title">{{ resource.title }}</h1>
      <div class="meta">
        <span>{{ resource.createTime }}</span>
        <span>{{ resource.points > 0 ? resource.points + ' 积分' : '免费' }}</span>
        <span v-if="resource.unlocked" class="unlocked">已解锁</span>
      </div>

      <div v-if="resource.cover" class="cover">
        <img :src="resource.cover" alt="资源封面" />
      </div>

      <div v-if="resource.description" class="summary">{{ resource.description }}</div>
      <!-- 详情内容由管理员在后台编辑，属可信 HTML -->
      <div class="content rich-text" v-html="resource.content"></div>

      <div class="action-box">
        <template v-if="resource.unlocked">
          <div class="resource-info">
            <div class="info-row">
              <span class="label">资源链接</span>
              <a class="link" :href="normalizeUrl(resource.url)" target="_blank" rel="noopener noreferrer">{{ resource.url }}</a>
              <el-button size="small" @click="copy(resource.url)">复制</el-button>
            </div>
            <div v-if="resource.password" class="info-row">
              <span class="label">提取码</span>
              <code class="password">{{ resource.password }}</code>
              <el-button size="small" @click="copy(resource.password)">复制</el-button>
            </div>
          </div>
          <a class="go-btn" :href="normalizeUrl(resource.url)" target="_blank" rel="noopener noreferrer">打开资源</a>
        </template>
        <template v-else>
          <p class="unlock-tip">
            前往资源需要消耗
            <strong>{{ resource.points > 0 ? resource.points : 0 }}</strong>
            积分
          </p>
          <el-button type="primary" size="large" :loading="unlocking" @click="onUnlock">
            {{ memberStore.isLogin ? '前往资源' : '登录后前往资源' }}
          </el-button>
        </template>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="资源不存在" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { portalResourceDetail, unlockResource } from '@/api/resource'
import { useMemberStore } from '@/store/member'
import { copyText } from '@/utils/content'

const route = useRoute()
const router = useRouter()
const memberStore = useMemberStore()

const resource = ref(null)
const loading = ref(false)
const unlocking = ref(false)

async function load() {
  loading.value = true
  try {
    resource.value = await portalResourceDetail(route.params.id)
  } finally {
    loading.value = false
  }
}

async function onUnlock() {
  if (!memberStore.isLogin) {
    ElMessage.warning('请先登录后再前往资源')
    router.push('/portal/login')
    return
  }
  unlocking.value = true
  try {
    resource.value = await unlockResource(route.params.id)
    ElMessage.success('已解锁资源')
    memberStore.fetchInfo(true).catch(() => {})
  } finally {
    unlocking.value = false
  }
}

function normalizeUrl(url) {
  if (!url) return '#'
  return /^https?:\/\//i.test(url) ? url : `https://${url}`
}

async function copy(text) {
  try {
    await copyText(text)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动复制')
  }
}

watch(() => route.params.id, load)
onMounted(load)
</script>

<style scoped>
.resource-detail {
  max-width: 820px;
  margin: 0 auto;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  backdrop-filter: blur(12px);
  padding: calc(var(--header-height) + 20px) 28px 28px;
  box-shadow: var(--shadow);
}
.title {
  font-size: 26px;
  margin: 0 0 12px;
  line-height: 1.4;
  color: var(--text-strong);
}
.meta {
  display: flex;
  gap: 16px;
  color: var(--text-muted);
  font-size: 13px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 20px;
}
.unlocked {
  color: #67c23a;
}
.cover {
  margin: 0 0 22px;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: var(--shadow);
}
.cover img {
  display: block;
  width: 100%;
  max-height: 420px;
  object-fit: cover;
}
.summary {
  margin-bottom: 16px;
  padding: 12px 16px;
  border-left: 3px solid var(--accent);
  background: var(--accent-soft);
  color: var(--text);
  border-radius: 8px;
  line-height: 1.7;
}
.content {
  word-break: break-word;
}
.action-box {
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
.resource-info {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}
.info-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.info-row .label {
  color: var(--text-muted);
  font-size: 13px;
  width: 70px;
}
.link {
  color: var(--accent);
  text-decoration: none;
  word-break: break-all;
}
.password {
  padding: 2px 10px;
  border-radius: 6px;
  background: var(--accent-soft);
  color: var(--accent);
}
.go-btn {
  display: inline-flex;
  align-items: center;
  padding: 10px 22px;
  border-radius: 999px;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  text-decoration: none;
  box-shadow: var(--shadow);
}
.unlock-tip {
  margin: 0 0 14px;
  color: var(--text-muted);
}
.unlock-tip strong {
  color: var(--accent);
}
@media (max-width: 768px) {
  .resource-detail {
    padding: 16px;
    padding-top: calc(var(--header-height) + 16px);
  }
  .title {
    font-size: 22px;
  }
}
</style>
