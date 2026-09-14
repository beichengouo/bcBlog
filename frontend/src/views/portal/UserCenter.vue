<template>
  <div class="user-page">
    <div v-if="loading" class="loading">加载中...</div>
    <template v-else-if="user">
      <div class="user-card glass">
        <div class="avatar-wrap" @click="pickAvatar">
          <img v-if="user.avatar" class="avatar" :src="user.avatar" alt="" />
          <div v-else class="avatar placeholder">{{ (user.nickname || user.username || 'U').slice(0, 1) }}</div>
          <span class="avatar-tip">点击上传头像</span>
          <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarChange" />
        </div>

        <div class="user-main">
          <div class="name-row">
            <h2 class="nickname">{{ user.nickname || user.username }}</h2>
            <span class="level-badge">Lv.{{ user.level }} {{ user.levelName }}</span>
            <span class="points-badge">积分 {{ user.points || 0 }}</span>
          </div>
          <p class="username">{{ user.email }}</p>

          <div class="exp-row">
            <div class="exp-bar">
              <div class="exp-fill" :style="{ width: expPercent + '%' }"></div>
            </div>
            <span class="exp-text">
              {{ user.exp }} / {{ user.nextLevelExp != null ? user.nextLevelExp : user.exp }} EXP
            </span>
          </div>

          <div class="actions">
            <el-button type="primary" :disabled="user.signedToday" @click="onSignIn">
              {{ user.signedToday ? '今日已签到' : '每日签到 +' + signExp }}
            </el-button>
            <span class="sign-days">累计签到 {{ user.signDays }} 天</span>
          </div>
        </div>
      </div>

      <el-card v-if="inviteCode" class="section">
        <template #header>我的邀请码</template>
        <div class="invite-row">
          <code class="invite-code">{{ inviteCode }}</code>
          <el-button size="small" type="primary" plain @click="copyInvite">复制</el-button>
        </div>
        <p class="muted">邀请码可无限次使用，短时间内被频繁使用会自动更换。</p>
      </el-card>

      <el-card class="section">
        <template #header>
          <div class="points-head">
            <span>我的积分</span>
            <span class="points-total">{{ user.points || 0 }} 分</span>
          </div>
        </template>
        <div v-if="pointLogs.length" class="point-list">
          <div v-for="p in pointLogs" :key="p.id" class="point-item">
            <span class="point-reason">{{ p.reason || p.type }}</span>
            <span class="point-change" :class="{ plus: p.points > 0, minus: p.points < 0 }">
              {{ p.points > 0 ? '+' + p.points : p.points }}
            </span>
            <span class="point-time">{{ p.createTime }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无积分记录" :image-size="60" />
        <el-pagination
          v-if="pointTotal > pointSize"
          v-model:current-page="pointPage"
          :page-size="pointSize"
          :total="pointTotal"
          layout="prev, pager, next"
          class="pager"
          @current-change="loadPoints"
        />
      </el-card>

      <el-card class="section">
        <template #header>我的评论</template>
        <div v-if="comments.length" class="comment-list">
          <div v-for="c in comments" :key="c.id" class="comment-item">
            <div class="comment-head">
              <span class="comment-article">{{ c.articleTitle || '文章' }}</span>
              <span class="comment-time">{{ c.createTime }}</span>
            </div>
            <p class="comment-content">{{ c.content }}</p>
          </div>
        </div>
        <el-empty v-else description="还没有发表过评论" :image-size="70" />
        <el-pagination
          v-if="commentTotal > commentSize"
          v-model:current-page="commentPage"
          :page-size="commentSize"
          :total="commentTotal"
          layout="prev, pager, next"
          class="pager"
          @current-change="loadComments"
        />
      </el-card>
    </template>

    <el-empty v-else description="请先登录">
      <el-button type="primary" @click="router.push('/portal/login')">去登录</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useMemberStore } from '@/store/member'
import { uploadMemberAvatar, memberSignIn, myComments, myInviteCode, myPointLogs } from '@/api/member'
import { copyText } from '@/utils/content'
import { getPortalConfig } from '@/api/config'

const router = useRouter()
const memberStore = useMemberStore()

const loading = ref(true)
const user = ref(null)
const inviteCode = ref('')
const signExp = ref(5)
const avatarInput = ref()
const comments = ref([])
const commentTotal = ref(0)
const commentPage = ref(1)
const commentSize = ref(5)
const pointLogs = ref([])
const pointTotal = ref(0)
const pointPage = ref(1)
const pointSize = ref(5)

const expPercent = computed(() => {
  if (!user.value) return 0
  const current = user.value.exp || 0
  const next = user.value.nextLevelExp
  if (!next || next <= current) return 100
  return Math.min(100, Math.round((current / next) * 100))
})

function pickAvatar() {
  avatarInput.value?.click()
}

async function onAvatarChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像不能超过 5MB')
    return
  }
  const data = await uploadMemberAvatar(file)
  memberStore.userInfo = data
  user.value = data
  ElMessage.success('头像已更新')
}

async function onSignIn() {
  const result = await memberSignIn()
  ElMessage.success(`签到成功，获得 ${result.gainedExp} 经验、${result.gainedPoints} 积分`)
  user.value = await memberStore.fetchInfo(true)
  loadPoints()
}

async function loadComments() {
  const data = await myComments({ page: commentPage.value, size: commentSize.value })
  comments.value = data.list || []
  commentTotal.value = data.total || 0
}

async function loadPoints() {
  const data = await myPointLogs({ page: pointPage.value, size: pointSize.value })
  pointLogs.value = data.list || []
  pointTotal.value = data.total || 0
}

async function copyInvite() {
  try {
    await copyText(inviteCode.value)
    ElMessage.success('邀请码已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动复制')
  }
}

onMounted(async () => {
  try {
    const config = await getPortalConfig()
    signExp.value = config.signExp || 5
  } catch (e) {
    // 使用默认值
  }
  user.value = await memberStore.fetchInfo(true)
  loading.value = false
  if (user.value) {
    if (user.value.canInvite === 1) {
      try {
        const code = await myInviteCode()
        inviteCode.value = code ? code.code : ''
      } catch (e) {
        inviteCode.value = ''
      }
    }
    loadComments()
    loadPoints()
  }
})
</script>

<style scoped>
.user-page {
  max-width: 900px;
  margin: 0 auto;
  padding: calc(var(--header-height) + 28px) 20px 40px;
}
.loading {
  text-align: center;
  color: var(--text-muted);
  padding: 80px 0;
}
.user-card {
  display: flex;
  gap: 24px;
  padding: 26px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  align-items: center;
}
.avatar-wrap {
  position: relative;
  cursor: pointer;
  flex-shrink: 0;
  text-align: center;
}
.avatar {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid var(--border);
  box-shadow: var(--shadow);
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar.placeholder {
  font-size: 34px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.avatar-tip {
  display: block;
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 12px;
}
.user-main {
  flex: 1;
  min-width: 0;
}
.name-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.nickname {
  margin: 0;
  font-size: 22px;
  color: var(--text-strong);
}
.level-badge {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.points-badge {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--accent);
  background: var(--accent-soft);
  border: 1px solid var(--border);
}
.username {
  margin: 6px 0 14px;
  color: var(--text-muted);
  font-size: 13px;
}
.exp-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.exp-bar {
  flex: 1;
  height: 10px;
  border-radius: 999px;
  background: var(--accent-soft);
  overflow: hidden;
}
.exp-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--accent), var(--accent-2));
  transition: width 0.4s ease;
}
.exp-text {
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
}
.actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.sign-days {
  color: var(--text-muted);
  font-size: 13px;
}
.section {
  margin-top: 18px;
}
.points-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.points-total {
  color: var(--accent);
  font-weight: 700;
}
.point-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border);
  font-size: 13px;
}
.point-item:last-child {
  border-bottom: none;
}
.point-reason {
  flex: 1;
  color: var(--text);
}
.point-change {
  font-weight: 700;
  color: var(--text-muted);
}
.point-change.plus {
  color: #67c23a;
}
.point-change.minus {
  color: #f56c6c;
}
.point-time {
  color: var(--text-muted);
  font-size: 12px;
}
.invite-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.invite-code {
  padding: 8px 16px;
  border-radius: 10px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 18px;
  letter-spacing: 2px;
}
.muted {
  margin: 10px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}
.comment-item {
  padding: 12px 0;
  border-bottom: 1px dashed var(--border);
}
.comment-item:last-child {
  border-bottom: none;
}
.comment-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}
.comment-article {
  color: var(--accent);
  font-size: 13px;
}
.comment-time {
  color: var(--text-muted);
  font-size: 12px;
}
.comment-content {
  margin: 0;
  color: var(--text);
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
@media (max-width: 640px) {
  .user-card {
    flex-direction: column;
    text-align: center;
  }
  .name-row,
  .actions {
    justify-content: center;
  }
}
</style>
