<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <span>安全设置</span>
        <div class="tags">
          <el-tag v-if="status.enabled === '0'" type="info" effect="dark">二次验证已关闭</el-tag>
          <el-tag v-else-if="!status.hasSecurityPassword" type="danger" effect="dark">未设置安全密码</el-tag>
          <el-tag v-else-if="status.verified" type="success">本次已通过验证</el-tag>
          <el-tag v-else type="warning">本次登录待验证</el-tag>
        </div>
      </div>
    </template>

    <!-- 总开关关掉时先说清楚：这时前后端都不会再要求安全密码 -->
    <el-alert
      v-if="status.enabled === '0'"
      class="tip"
      type="info"
      show-icon
      :closable="false"
      title="二次验证总开关已关闭"
      description="敏感菜单与敏感接口都不再要求输入安全密码（后端同样不校验）。要恢复保护，到「登录保护」标签页把「二次验证总开关」打开即可。"
    />
    <!-- 没有安全密码时，敏感操作会直接被后端拒绝，这里给出显眼提示 -->
    <el-alert
      v-else-if="!status.hasSecurityPassword"
      class="tip"
      type="warning"
      show-icon
      :closable="false"
      title="还没有设置安全密码"
      description="安全密码用于「用户管理、邀请码、AI 服务商、清理任务」等敏感操作的二次验证。未设置时这些操作会提示「请先到系统安全 → 安全设置设置安全密码」。"
    />

    <el-tabs v-model="tab">
      <!-- ==================== 安全密码 ==================== -->
      <el-tab-pane label="安全密码" name="password">
        <el-descriptions :column="isMobile ? 1 : 2" border class="desc">
          <el-descriptions-item label="当前账号">{{ status.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="安全密码">
            <el-tag :type="status.hasSecurityPassword ? 'success' : 'danger'" size="small">
              {{ status.hasSecurityPassword ? '已设置' : '未设置' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="本次验证状态">
            <el-tag :type="status.verified ? 'success' : 'info'" size="small">
              {{ status.verified ? '已验证' : '未验证' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="验证有效期">
            {{ verifyMinutesText }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">{{ status.hasSecurityPassword ? '修改安全密码' : '设置安全密码' }}</el-divider>

        <div class="rule-box">
          <div class="rule-title">哪些菜单需要二次验证</div>
          <div class="rule-body">
            系统设置、登录日志、API 调用审计、积分管理、等级配置、管理员管理、用户管理、邀请码管理、
            Gitalk 评论、邮件管理、DeepSeek、API 管理、第三方接口，以及沙盒世界下的世界/角色/行动/集市。
          </div>
          <div class="rule-body">
            规则：本次登录内，每个菜单第一次进入时要输入安全密码（换菜单需要再验证一次）；
            点「取消」不会进入该菜单，页面也不会加载数据；刷新页面后需要重新验证。
          </div>
        </div>

        <el-form
          ref="pwdFormRef"
          :model="pwdForm"
          :rules="pwdRules"
          :label-width="isMobile ? 'auto' : '130px'"
          :label-position="isMobile ? 'top' : 'right'"
          class="form"
        >
          <el-form-item label="登录密码" prop="loginPassword">
            <el-input
              v-model="pwdForm.loginPassword"
              type="password"
              show-password
              autocomplete="off"
              placeholder="用于确认是本人操作"
            />
          </el-form-item>
          <el-form-item label="新安全密码" prop="securityPassword">
            <el-input
              v-model="pwdForm.securityPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="至少 6 位，且不能与登录密码相同"
            />
          </el-form-item>
          <el-form-item label="确认安全密码" prop="confirmPassword">
            <el-input
              v-model="pwdForm.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="请再次输入新安全密码"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingPwd" @click="onSavePassword">
              {{ status.hasSecurityPassword ? '修改安全密码' : '设置安全密码' }}
            </el-button>
            <el-button
              v-if="status.enabled === '1' && status.hasSecurityPassword && !status.verified"
              plain
              :loading="verifying"
              @click="onVerifyNow"
            >
              立即验证
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ==================== 登录保护 ==================== -->
      <el-tab-pane label="登录保护" name="protect">
        <el-form
          :model="settings"
          :label-width="isMobile ? 'auto' : '150px'"
          :label-position="isMobile ? 'top' : 'right'"
          class="form"
        >
          <el-form-item label="二次验证总开关">
            <el-switch v-model="settings.enabled" active-value="1" inactive-value="0" />
            <span class="hint">关闭后敏感操作不再要求输入安全密码（不建议关闭）</span>
          </el-form-item>
          <el-form-item label="验证有效期">
            <el-input-number v-model="settings.verifyMinutes" :min="0" :max="1440" />
            <span class="hint">单位为分钟；验证一次后在该时间内免验证，填 0 表示本次登录内一直有效</span>
          </el-form-item>
          <el-form-item label="安全邮箱">
            <el-input v-model="settings.securityEmail" placeholder="用于接收异常登录提醒，如 xxxx@qq.com" maxlength="100" />
            <span class="hint">留空则不发送异常登录提醒邮件</span>
          </el-form-item>
          <el-form-item label="异常登录邮件提醒">
            <el-switch v-model="settings.alertEnabled" active-value="1" inactive-value="0" />
            <span class="hint">异地 / 深夜 / 连续失败登录时发邮件提醒（不阻断登录）</span>
          </el-form-item>
          <el-form-item label="连续失败提醒次数">
            <el-input-number v-model="settings.alertFailTimes" :min="0" :max="20" />
            <span class="hint">连续登录失败几次后发送提醒，填 0 表示不提醒</span>
          </el-form-item>
          <el-form-item label="深夜时段">
            <el-time-picker
              v-model="settings.nightStart"
              format="HH:mm"
              value-format="HH:mm"
              placeholder="开始"
              class="time"
            />
            <span class="sep">至</span>
            <el-time-picker
              v-model="settings.nightEnd"
              format="HH:mm"
              value-format="HH:mm"
              placeholder="结束"
              class="time"
            />
            <span class="hint">该时段内的登录视为异常并发送提醒</span>
          </el-form-item>
          <el-form-item label="单点登录">
            <el-switch v-model="settings.singleLogin" active-value="1" inactive-value="0" />
            <span class="hint">开启后同一管理员只允许一处后台在线，新登录会踢掉旧会话</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingSettings" @click="onSaveSettings">保存设置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ==================== 在线会话 ==================== -->
      <el-tab-pane label="在线会话" name="session">
        <div class="session-head">
          <span class="hint">这里能看到当前所有已登录的后台会话，可以把非本人的会话踢下线（只注销会话，不封号）</span>
          <el-button :loading="sessionLoading" @click="loadSessions">刷新</el-button>
        </div>
        <el-table :data="sessions" v-loading="sessionLoading">
          <el-table-column prop="username" label="账号" width="120" />
          <el-table-column prop="nickname" label="昵称" width="120" />
          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.role === 'SUPER' ? 'danger' : 'info'">{{ roleLabel(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="device" label="设备" min-width="180" show-overflow-tooltip />
          <el-table-column prop="loginTime" label="登录时间" width="170" />
          <el-table-column label="令牌" width="120">
            <template #default="{ row }">
              <span class="muted">{{ row.token }}</span>
              <el-tag v-if="row.tokenValue === currentToken" size="small" type="success" class="cur-tag">当前</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button
                size="small"
                type="danger"
                plain
                :disabled="row.tokenValue === currentToken"
                @click="onKick(row)"
              >
                踢下线
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  securityStatus,
  securityVerify,
  saveSecurityPassword,
  saveSecuritySettings,
  securitySessions,
  kickSession
} from '@/api/security'

const tab = ref('password')
const isMobile = ref(false)
const pwdFormRef = ref(null)

// 安全设置状态
const status = reactive({
  username: '',
  hasSecurityPassword: false,
  verified: false,
  securityEmail: '',
  enabled: '1',
  verifyMinutes: '30',
  alertEnabled: '1',
  nightStart: '00:00',
  nightEnd: '06:00',
  alertFailTimes: '3',
  singleLogin: '1'
})

// 设置安全密码表单
const pwdForm = reactive({ loginPassword: '', securityPassword: '', confirmPassword: '' })
const savingPwd = ref(false)
const verifying = ref(false)

// 登录保护设置表单
const settings = reactive({
  enabled: '1',
  verifyMinutes: 30,
  securityEmail: '',
  alertEnabled: '1',
  alertFailTimes: 3,
  nightStart: '00:00',
  nightEnd: '06:00',
  singleLogin: '1'
})
const savingSettings = ref(false)

// 在线会话
const sessions = ref([])
const sessionLoading = ref(false)
const currentToken = localStorage.getItem('token') || ''

const pwdRules = {
  loginPassword: [{ required: true, message: '请输入登录密码', trigger: 'blur' }],
  securityPassword: [
    { required: true, message: '请输入新安全密码', trigger: 'blur' },
    { min: 6, message: '安全密码至少 6 位', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value && pwdForm.loginPassword && value === pwdForm.loginPassword) {
          callback(new Error('安全密码不能与登录密码相同'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新安全密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.securityPassword) {
          callback(new Error('两次输入的安全密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 验证有效期的展示文案
const verifyMinutesText = computed(() => {
  const minutes = Number(status.verifyMinutes)
  if (!minutes || minutes <= 0) {
    return '本次登录内一直有效'
  }
  return `${minutes} 分钟`
})

function roleLabel(role) {
  if (role === 'SUPER') return '超级管理员'
  if (role === 'ADMIN2') return '二级管理员'
  if (role === 'ADMIN1') return '一级管理员'
  return role || '-'
}

async function load() {
  const data = await securityStatus()
  Object.assign(status, data || {})
  // 回填「登录保护」表单
  settings.enabled = data.enabled || '0'
  settings.verifyMinutes = Number(data.verifyMinutes || 30)
  settings.securityEmail = data.securityEmail || ''
  settings.alertEnabled = data.alertEnabled || '0'
  settings.alertFailTimes = Number(data.alertFailTimes || 0)
  settings.nightStart = data.nightStart || '00:00'
  settings.nightEnd = data.nightEnd || '06:00'
  settings.singleLogin = data.singleLogin || '0'
}

async function loadSessions() {
  sessionLoading.value = true
  try {
    sessions.value = await securitySessions()
  } finally {
    sessionLoading.value = false
  }
}

// 保存安全密码：需要登录密码确认，成功后后端会直接标记本次已验证
async function onSavePassword() {
  if (!pwdFormRef.value) return
  try {
    await pwdFormRef.value.validate()
  } catch (e) {
    return
  }
  try {
    await ElMessageBox.confirm(
      status.hasSecurityPassword ? '确定要修改安全密码吗？修改后其它会话需要重新验证。' : '确定要设置该安全密码吗？',
      '安全密码',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  savingPwd.value = true
  try {
    await saveSecurityPassword({
      loginPassword: pwdForm.loginPassword,
      securityPassword: pwdForm.securityPassword
    })
    ElMessage.success('安全密码已保存，本次登录内无需再验证')
    pwdForm.loginPassword = ''
    pwdForm.securityPassword = ''
    pwdForm.confirmPassword = ''
    await load()
  } finally {
    savingPwd.value = false
  }
}

// 主动验证一次安全密码
async function onVerifyNow() {
  try {
    const { value } = await ElMessageBox.prompt('请输入安全密码', '安全验证', {
      confirmButtonText: '验证',
      cancelButtonText: '取消',
      inputType: 'password',
      inputPlaceholder: '请输入安全密码'
    })
    verifying.value = true
    await securityVerify(value)
    ElMessage.success('验证通过')
    await load()
  } catch (e) {
    // 取消或验证失败都已提示，这里忽略
  } finally {
    verifying.value = false
  }
}

async function onSaveSettings() {
  savingSettings.value = true
  try {
    await saveSecuritySettings({
      securityEmail: settings.securityEmail,
      enabled: settings.enabled,
      alertEnabled: settings.alertEnabled,
      verifyMinutes: settings.verifyMinutes,
      nightStart: settings.nightStart,
      nightEnd: settings.nightEnd,
      alertFailTimes: settings.alertFailTimes,
      singleLogin: settings.singleLogin
    })
    ElMessage.success('安全设置已保存')
    await load()
  } finally {
    savingSettings.value = false
  }
}

async function onKick(row) {
  try {
    await ElMessageBox.confirm(`确定把「${row.nickname || row.username}」的这个会话踢下线吗？`, '踢下线', {
      type: 'warning',
      confirmButtonText: '踢下线',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await kickSession(row.tokenValue)
  ElMessage.success('已踢下线')
  loadSessions()
}

function onResize() {
  isMobile.value = window.innerWidth < 768
}

onMounted(async () => {
  onResize()
  window.addEventListener('resize', onResize)
  try {
    await load()
  } catch (e) {
    // 加载失败时保持默认值，错误信息由请求拦截器统一提示
  }
  loadSessions()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.tags {
  display: flex;
  gap: 8px;
}
.tip {
  margin-bottom: 12px;
}
.desc {
  margin-bottom: 8px;
}
.rule-box {
  margin: 4px 0 8px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
  line-height: 1.8;
}
.rule-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.rule-body {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.form {
  max-width: 720px;
}
.hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.time {
  width: 130px;
}
.sep {
  margin: 0 8px;
  color: #909399;
}
.session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.session-head .hint {
  margin-left: 0;
}
.cur-tag {
  margin-left: 6px;
}
.muted {
  color: #909399;
}
/* 移动端：提示文字换行显示，避免被挤压 */
@media (max-width: 767px) {
  .hint {
    display: block;
    margin: 6px 0 0;
  }
  .sep {
    margin: 0 6px;
  }
}
</style>
