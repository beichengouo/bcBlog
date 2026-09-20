<template>
  <el-card class="settings-card">
    <template #header>系统设置</template>
    <el-form :model="form" label-width="120px">
      <el-form-item label="站点名称">
        <el-input v-model="form.siteName" placeholder="站点名称" maxlength="100" />
      </el-form-item>
      <el-form-item label="站点 Logo">
        <div class="logo-box">
          <el-avatar :size="64" shape="circle" :src="form.siteLogo" class="logo-preview">
            <span>Logo</span>
          </el-avatar>
          <div class="logo-actions">
            <el-button size="small" @click="onPickLogo">上传图片</el-button>
            <el-button size="small" type="danger" plain :disabled="!form.siteLogo" @click="onDeleteLogo">
              删除恢复默认
            </el-button>
            <span class="logo-tip">支持 jpg / png / gif / webp，建议使用正方形图片</span>
          </div>
          <input ref="logoInput" type="file" accept="image/*" hidden @change="onLogoChange" />
        </div>
      </el-form-item>
      <el-form-item label="备案号">
        <el-input v-model="form.siteIcp" placeholder="如：京ICP备xxxxxx号" maxlength="100" />
      </el-form-item>
      <el-form-item label="SEO 描述">
        <el-input v-model="form.siteDescription" type="textarea" :rows="2" placeholder="站点描述" maxlength="300" />
      </el-form-item>
      <el-form-item label="SEO 关键词">
        <el-input v-model="form.siteKeywords" placeholder="关键词，英文逗号分隔" maxlength="200" />
      </el-form-item>
      <el-form-item label="首页标语">
        <el-input v-model="form.siteSlogan" placeholder="首页标题下方轮播语，如：愿每一次点击都有温度" maxlength="100" />
      </el-form-item>
      <el-form-item label="首页文章轮播">
        <el-switch v-model="form.homeCarouselEnabled" :active-value="1" :inactive-value="0" active-text="显示" />
      </el-form-item>
      <el-form-item label="轮播文章数量">
        <el-input-number v-model="form.homeCarouselCount" :min="1" :max="10" />
        <span class="field-tip">范围 1 ~ 10，展示最新文章</span>
      </el-form-item>
      <el-divider content-position="left">评论与用户</el-divider>
      <el-form-item label="评论系统">
        <el-radio-group v-model="form.commentSystem">
          <el-radio value="gitalk">Gitalk</el-radio>
          <el-radio value="native">原生评论</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="注册需邀请码">
        <el-switch v-model="form.registerInviteRequired" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item label="注册需邮箱验证">
        <el-switch v-model="form.registerEmailVerify" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item label="签到经验">
        <el-input-number v-model="form.signExp" :min="0" :max="1000" />
      </el-form-item>
      <el-form-item label="评论经验">
        <el-input-number v-model="form.commentExp" :min="0" :max="1000" />
        <span class="field-tip">每天前几次评论获得经验</span>
        <el-input-number v-model="form.commentExpLimit" :min="0" :max="100" class="inline-number" />
      </el-form-item>
      <el-divider content-position="left">数据清理</el-divider>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="cleanup-alert"
        title="数据清理只删除历史日志类数据，不会删除文章、评论、资源、用户和积分余额。保留天数设置过小会导致历史记录很快丢失，请谨慎修改。"
      />
      <el-form-item label="定期清理">
        <el-switch v-model="form.cleanupEnabled" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item label="执行时间">
        <el-time-picker v-model="form.cleanupTime" value-format="HH:mm" format="HH:mm" placeholder="选择每天执行时间" />
        <span class="field-tip">每天到达该时间自动执行一次，不需要重启后端</span>
      </el-form-item>
      <el-form-item label="登录日志保留">
        <div class="cleanup-field">
          <el-input-number v-model="form.cleanupLoginLogDays" :min="1" :max="3650" />
          <span class="field-tip">天</span>
        </div>
        <div class="cleanup-desc">记录管理员登录成功/失败、IP、浏览器信息。清理后登录日志页面只能看到保留期内的记录，不影响账号安全。建议 7～30 天。</div>
      </el-form-item>
      <el-form-item label="访问统计保留">
        <div class="cleanup-field">
          <el-input-number v-model="form.cleanupVisitStatDays" :min="1" :max="3650" />
          <span class="field-tip">天</span>
        </div>
        <div class="cleanup-desc">记录每天前台访问量 PV。清理后仪表盘访问趋势只能看到保留期内的数据，不影响文章浏览量和站点运行。建议 30～90 天。</div>
      </el-form-item>
      <el-form-item label="签到记录保留">
        <div class="cleanup-field">
          <el-input-number v-model="form.cleanupSignLogDays" :min="1" :max="3650" />
          <span class="field-tip">天</span>
        </div>
        <div class="cleanup-desc">记录用户每日签到明细。清理不影响用户的累计签到天数和积分余额，只是看不到更早的签到明细。建议 30～180 天。</div>
      </el-form-item>
      <el-form-item label="积分流水保留">
        <div class="cleanup-field">
          <el-input-number v-model="form.cleanupPointLogDays" :min="1" :max="3650" />
          <span class="field-tip">天</span>
        </div>
        <div class="cleanup-desc">记录积分变动明细。清理不影响积分余额，但用户中心和后台看不到更早的积分记录；如果积分以后要接商城或交易，建议保留 90 天以上。</div>
      </el-form-item>
      <el-form-item label="沙盒行动日志保留">
        <div class="cleanup-field">
          <el-input-number v-model="form.cleanupSandboxActDays" :min="1" :max="3650" />
          <span class="field-tip">天</span>
        </div>
        <div class="cleanup-desc">
          沙盒角色的行动记录（sandbox_act），前台时间线、角色档案「最近行动」都读它。
          清理后前台只能看到保留期内的行动；角色的长期记忆是单独的「每日记忆」，不受影响。建议 7～30 天。
        </div>
      </el-form-item>
      <el-form-item label="沙盒记忆保留">
        <div class="cleanup-field">
          <el-input-number v-model="form.cleanupSandboxMemoryDays" :min="1" :max="3650" />
          <span class="field-tip">天</span>
        </div>
        <div class="cleanup-desc">
          沙盒角色的每日记忆（sandbox_memory），是角色"记得前几天发生过什么"的依据。
          清理后角色会忘掉更早的事，但不会影响当前状态、金币、好感度与背包。建议 30～180 天。
        </div>
      </el-form-item>
      <el-form-item label="旅人委托保留">
        <div class="cleanup-field">
          <el-input-number v-model="form.cleanupSandboxQuestDays" :min="1" :max="3650" />
          <span class="field-tip">天</span>
        </div>
        <div class="cleanup-desc">
          旅人委托板（sandbox_quest）：已完成的委托与没人接的旧批次委托会保留这么久，方便角色档案里
          「最近完成的委托」和委托板上的「已被 XX 完成」有东西可显示；<b>接取中的委托永远不会被清理</b>。
          建议 3～15 天。
        </div>
      </el-form-item>
      <el-form-item label="API 调用审计保留">
        <div class="cleanup-field">
          <el-input-number v-model="form.cleanupAdminApiLogDays" :min="1" :max="3650" />
          <span class="field-tip">天</span>
        </div>
        <div class="cleanup-desc">
          记录谁在什么时候调用了会消耗额度或涉及密钥的功能（admin_api_log）。
          清理只是删掉历史记录，不影响功能使用。建议 3～30 天。
        </div>
      </el-form-item>
      <el-form-item>
        <!-- 立即清理会真的删数据，后端也只允许超级管理员调用，这里对普通管理员隐藏 -->
        <el-button v-if="isSuper" type="warning" plain :loading="cleaning" @click="onCleanup">立即清理一次</el-button>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ElMessageBox } from 'element-plus'
import { getConfig, saveConfig, uploadSiteLogo, deleteSiteLogo } from '@/api/config'
import { runCleanup } from '@/api/system'
import { applySiteMeta } from '@/utils/siteMeta'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
/** 只有超级管理员能「立即清理」：这是破坏性操作，后端也只放行超管 */
const isSuper = computed(() => userStore.userInfo && userStore.userInfo.role === 'SUPER')
const saving = ref(false)
const cleaning = ref(false)
const logoInput = ref()
const form = reactive({
  siteName: '',
  siteLogo: '',
  siteIcp: '',
  siteDescription: '',
  siteKeywords: '',
  siteSlogan: '',
  homeCarouselEnabled: 1,
  homeCarouselCount: 5,
  commentSystem: 'gitalk',
  registerInviteRequired: 0,
  registerEmailVerify: 1,
  signExp: 5,
  commentExp: 3,
  commentExpLimit: 3,
  cleanupEnabled: 1,
  cleanupTime: '03:30',
  cleanupLoginLogDays: 7,
  cleanupVisitStatDays: 30,
  cleanupSignLogDays: 30,
  cleanupPointLogDays: 30,
  cleanupSandboxActDays: 7,
  cleanupSandboxMemoryDays: 30,
  cleanupSandboxQuestDays: 3,
  cleanupAdminApiLogDays: 3
})

async function load() {
  const data = await getConfig()
  form.siteName = data.siteName || ''
  form.siteLogo = data.siteLogo || ''
  form.siteIcp = data.siteIcp || ''
  form.siteDescription = data.siteDescription || ''
  form.siteKeywords = data.siteKeywords || ''
  form.siteSlogan = data.siteSlogan || ''
  form.homeCarouselEnabled = data.homeCarouselEnabled === 0 ? 0 : 1
  form.homeCarouselCount = data.homeCarouselCount || 5
  form.commentSystem = data.commentSystem || 'gitalk'
  form.registerInviteRequired = data.registerInviteRequired === 1 ? 1 : 0
  form.registerEmailVerify = data.registerEmailVerify === 0 ? 0 : 1
  form.signExp = data.signExp == null ? 5 : data.signExp
  form.commentExp = data.commentExp == null ? 3 : data.commentExp
  form.commentExpLimit = data.commentExpLimit == null ? 3 : data.commentExpLimit
  form.cleanupEnabled = data.cleanupEnabled === 0 ? 0 : 1
  form.cleanupTime = data.cleanupTime || '03:30'
  form.cleanupLoginLogDays = data.cleanupLoginLogDays || 7
  form.cleanupVisitStatDays = data.cleanupVisitStatDays || 30
  form.cleanupSignLogDays = data.cleanupSignLogDays || 30
  form.cleanupPointLogDays = data.cleanupPointLogDays || 30
  form.cleanupSandboxActDays = data.cleanupSandboxActDays || 7
  form.cleanupSandboxMemoryDays = data.cleanupSandboxMemoryDays || 30
  form.cleanupSandboxQuestDays = data.cleanupSandboxQuestDays || 3
  form.cleanupAdminApiLogDays = data.cleanupAdminApiLogDays || 3
}

async function onSave() {
  // 保留天数过小可能误删历史数据，先提醒确认
  if (form.cleanupEnabled === 1) {
    const warnings = []
    if (form.cleanupLoginLogDays < 7) warnings.push('登录日志保留小于 7 天')
    if (form.cleanupVisitStatDays < 30) warnings.push('访问统计保留小于 30 天')
    if (form.cleanupSignLogDays < 30) warnings.push('签到记录保留小于 30 天')
    if (form.cleanupPointLogDays < 30) warnings.push('积分流水保留小于 30 天')
    if (warnings.length) {
      try {
        await ElMessageBox.confirm(`${warnings.join('、')}，历史记录会很快被清理，确定保存吗？`, '数据清理提醒', { type: 'warning' })
      } catch (e) {
        return
      }
    }
  }
  saving.value = true
  try {
    await saveConfig({ ...form })
    // 保存后立即同步浏览器标签页名称和 Logo
    applySiteMeta({ ...form })
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

async function onCleanup() {
  try {
    await ElMessageBox.confirm(
      `将按当前保留天数清理：登录日志 ${form.cleanupLoginLogDays} 天前、访问统计 ${form.cleanupVisitStatDays} 天前、签到记录 ${form.cleanupSignLogDays} 天前、积分流水 ${form.cleanupPointLogDays} 天前的数据，删除后不可恢复。确定执行吗？`,
      '立即清理',
      { type: 'warning', confirmButtonText: '确定清理' }
    )
  } catch (e) {
    return
  }
  cleaning.value = true
  try {
    const result = await runCleanup()
    ElMessage.success(
      `清理完成：登录日志 ${result.loginLog} 条，访问统计 ${result.visitStat} 条，签到记录 ${result.signLog} 条，积分流水 ${result.pointLog} 条`
    )
  } finally {
    cleaning.value = false
  }
}

function onPickLogo() {
  logoInput.value?.click()
}

async function onLogoChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) {
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('Logo 图片大小不能超过 5MB')
    return
  }
  const url = await uploadSiteLogo(file)
  form.siteLogo = url
  applySiteMeta({ ...form })
  ElMessage.success('Logo 已更新，点击“保存”可同步其他设置')
}

async function onDeleteLogo() {
  await deleteSiteLogo()
  form.siteLogo = '/uploads/logo/avatar.png'
  applySiteMeta({ ...form })
  ElMessage.success('已恢复默认 Logo')
}

onMounted(load)
</script>

<style scoped>
.settings-card {
  max-width: 640px;
}
.logo-box {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  flex-wrap: wrap;
}
.logo-preview {
  flex-shrink: 0;
  border: 1px solid var(--border);
  background: var(--glass-bg);
}
.logo-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
.logo-tip {
  font-size: 12px;
  color: var(--text-muted);
}
.field-tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--text-muted);
}
.inline-number {
  margin-left: 10px;
}
.cleanup-alert {
  margin-bottom: 18px;
}
.cleanup-field {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cleanup-desc {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
</style>
