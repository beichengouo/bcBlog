<template>
  <div class="site-ai">
    <el-dialog v-model="digestVisible" title="今日运行简报（她会读到的内容）" width="min(92vw, 620px)">
      <pre class="digest-box">{{ digestText }}</pre>
      <template #footer>
        <el-button @click="digestVisible = false">关闭</el-button>
      </template>
    </el-dialog>
    <el-card>
      <template #header>
        <div class="toolbar">
          <div class="toolbar-left">
            <span class="title">网站AI · IRIS</span>
            <el-tag :type="form.enabled === 1 ? 'success' : 'info'" size="small">
              {{ form.enabled === 1 ? '运行中' : '已停用' }}
            </el-tag>
          </div>
          <div class="toolbar-right">
            <el-button size="small" @click="loadAll">刷新</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="tab">
        <!-- ================= 概览 ================= -->
        <el-tab-pane label="概览" name="overview">
          <el-row :gutter="12" class="stats">
            <el-col :xs="12" :sm="6"><div class="stat"><span class="num">{{ overview.todayCalls || 0 }}</span><span class="lbl">今日调用 / 上限 {{ form.dailyLimit || 6 }}</span></div></el-col>
            <el-col :xs="12" :sm="6"><div class="stat"><span class="num">{{ overview.todayArticles || 0 }}</span><span class="lbl">今日文章 / 上限 {{ form.articleDailyLimit || 1 }}</span></div></el-col>
            <el-col :xs="12" :sm="6"><div class="stat"><span class="num">{{ overview.todayComments || 0 }}</span><span class="lbl">今日评论</span></div></el-col>
            <el-col :xs="12" :sm="6"><div class="stat"><span class="num">{{ overview.todayReplies || 0 }}</span><span class="lbl">今日回复 / 上限 {{ form.replyDailyLimit || 5 }}</span></div></el-col>
          </el-row>

          <div class="status-line" v-if="overview.plan">
            <span class="muted">今天的写作计划：</span>{{ overview.plan }}
          </div>
          <div class="status-line" v-if="overview.statusLine">
            <span class="muted">当前状态：</span>{{ overview.statusLine }}
          </div>

          <div class="actions">
            <el-button type="primary" :loading="running === 'article'" @click="onRun('article')">立即写一篇文章</el-button>
            <el-button :loading="running === 'comment'" @click="onRun('comment')">立即评论一条</el-button>
            <el-button :loading="running === 'status'" @click="onRun('status')">立即更新状态</el-button>
            <el-button :loading="running === 'report'" @click="onRun('report')">立即写运行情况</el-button>
            <el-button :loading="running === 'memory'" @click="onMemory">生成今天的记忆</el-button>
          </div>
          <div class="tip">
            「立即执行」会真的调用 AI 并发布内容（写文章 = 直接发布、评论 = 直接展示）。
            停用状态下也可以手动执行，方便你调试。
          </div>

          <el-table :data="overview.recent || []" size="small" class="mt">
            <el-table-column prop="createTime" label="时间" width="160" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ typeText(row.activityType) }}</template>
            </el-table-column>
            <el-table-column prop="title" label="标题 / 目标" min-width="160" show-overflow-tooltip />
            <el-table-column label="内容" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ row.content }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ================= 参数设置 ================= -->
        <el-tab-pane label="参数设置" name="settings">
          <el-form label-width="150px" class="setting-form">
            <el-form-item label="今日调用计数">
              <span class="muted">
                今日已调用 {{ overview.todayCalls || 0 }} 次 / 上限 {{ form.dailyLimit || 6 }}；
                已写文章 {{ overview.todayArticles || 0 }} 篇 / 上限 {{ form.articleDailyLimit || 1 }}
              </span>
              <el-button size="small" style="margin-left: 10px" :loading="resetting" @click="onResetCounters">
                重置今日计数
              </el-button>
              <span class="tip">测试用：只把计数起点推到现在，<b>不删除任何活动日志</b></span>
            </el-form-item>            <el-form-item label="敏感词过滤">
              <el-switch v-model="form.sensitiveFilterEnabled" :active-value="1" :inactive-value="0" />
              <span class="tip">
                发布前是否过站内词库（默认开）。关掉后只依赖 AI 服务商自身的判断；
                开启时若被拦下，活动日志里会显示<b>命中的片段</b>，方便判断是误拦还是真问题
              </span>
            </el-form-item>            <el-form-item label="总开关">
              <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
              <span class="tip">关闭后不再定时调用；「立即执行」仍可用</span>
            </el-form-item>
            <el-form-item label="每日调用上限">
              <el-input-number v-model="form.dailyLimit" :min="1" :max="500" />
              <span class="tip">所有类型合计（上限 500，测试时可以调很大）</span>
            </el-form-item>

            <el-divider content-position="left">写文章</el-divider>
            <el-form-item label="启用">
              <el-switch v-model="form.articleEnabled" :active-value="1" :inactive-value="0" />
              <el-input v-model="form.articleTime" style="width: 90px; margin-left: 12px" placeholder="09:00" />
              <span class="tip">执行时间 HH:mm</span>
            </el-form-item>
            <el-form-item label="写作窗口">
              <el-input v-model="form.articleWindowStart" style="width: 90px" placeholder="09:00" />
              <span class="muted" style="margin: 0 6px">至</span>
              <el-input v-model="form.articleWindowEnd" style="width: 90px" placeholder="22:00" />
              <el-switch
                v-model="form.articleRandom"
                :active-value="1"
                :inactive-value="0"
                active-text="时间随机"
                inactive-text="均匀分布"
                style="margin-left: 12px"
              />
              <div class="tip">
                每天写几篇由上面的「每天最多几篇」决定；时间由服务端在这个窗口里排布
                （随机更像真人，均匀则更好预期）。当天的计划会在概览里列出来
              </div>
            </el-form-item>            <el-form-item label="每天最多几篇">
              <el-input-number v-model="form.articleDailyLimit" :min="1" :max="100" />
            </el-form-item>
            <el-form-item label="选题偏好">
              <el-input
                v-model="form.articleTopics"
                type="textarea"
                :rows="4"
                style="width: 640px"
                placeholder="想让她多写什么：例如技术教程（Git、Java、前端）、日常随笔、工具与读书笔记"
              />
              <div class="tip">这段会作为【主人给你的选题偏好】注入提示词，她优先从这里选题；不写就按人设自由发挥</div>
            </el-form-item>
            <el-form-item label="禁忌与边界">
              <el-input
                v-model="form.articleAvoid"
                type="textarea"
                :rows="4"
                style="width: 640px"
                placeholder="不许写什么：例如不要编造具体事件、不要虚构本站内部数据与日志"
              />
              <div class="tip">
                这是抑制"编造内容"的关键：提示词里同时给了她<b>真实素材</b>（她今天的活动、最近记忆、近期文章标题），
                并规定<b>只能依据这些材料写作</b>
              </div>
            </el-form-item>            <el-form-item label="服务商 / 模型">
              <el-select v-model="form.articleProviderId" clearable placeholder="默认系统服务商" style="width: 200px" @change="loadModels('article')">
                <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
              <el-select v-model="form.articleModel" filterable allow-create clearable placeholder="模型（建议用 pro）" style="width: 240px; margin-left: 8px">
                <el-option v-for="m in models.article" :key="m" :label="m" :value="m" />
              </el-select>
              <el-button style="margin-left: 8px" @click="loadModels('article')">获取模型</el-button>
            </el-form-item>

            <el-divider content-position="left">评论吐槽</el-divider>
            <el-form-item label="启用">
              <el-switch v-model="form.commentEnabled" :active-value="1" :inactive-value="0" />
              <el-input v-model="form.commentTime" style="width: 90px; margin-left: 12px" placeholder="15:00" />
            </el-form-item>
            <el-form-item label="评论范围">
              <el-select v-model="form.commentScope" style="width: 260px">
                <el-option label="最新文章 + 站长的文章" value="latest+owner" />
                <el-option label="只评论最新文章" value="latest" />
              </el-select>
            </el-form-item>
            <el-form-item label="服务商 / 模型">
              <el-select v-model="form.commentProviderId" clearable placeholder="默认系统服务商" style="width: 200px" @change="loadModels('comment')">
                <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
              <el-select v-model="form.commentModel" filterable allow-create clearable placeholder="模型（flash 即可）" style="width: 240px; margin-left: 8px">
                <el-option v-for="m in models.comment" :key="m" :label="m" :value="m" />
              </el-select>
              <el-button style="margin-left: 8px" @click="loadModels('comment')">获取模型</el-button>
            </el-form-item>

            <el-divider content-position="left">回复读者</el-divider>
            <el-form-item label="启用">
              <el-switch v-model="form.replyEnabled" :active-value="1" :inactive-value="0" />
              <span class="tip">只回复「她自己内容下」的留言</span>
            </el-form-item>
            <el-form-item label="冷却 / 每天上限">
              <el-input-number v-model="form.replyCooldownMinutes" :min="0" :max="1440" />
              <span class="tip">分钟</span>
              <el-input-number v-model="form.replyDailyLimit" :min="0" :max="50" style="margin-left: 16px" />
              <span class="tip">条 / 天</span>
            </el-form-item>
            <el-form-item label="服务商 / 模型">
              <el-select v-model="form.replyProviderId" clearable placeholder="默认系统服务商" style="width: 200px" @change="loadModels('reply')">
                <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
              <el-select v-model="form.replyModel" filterable allow-create clearable placeholder="模型（flash 即可）" style="width: 240px; margin-left: 8px">
                <el-option v-for="m in models.reply" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>

            <el-divider content-position="left">状态与记忆</el-divider>
            <el-form-item label="状态更新时间">
              <el-input v-model="form.musingTime" style="width: 90px" placeholder="21:00" />
              <span class="tip">每天这个时间更新主页上那句「当前状态」</span>
            </el-form-item>
            <el-form-item label="记忆保留天数">
              <el-input-number v-model="form.memoryDays" :min="1" :max="30" />
              <span class="tip">同时决定写作时携带几天的记忆</span>
            </el-form-item>
            <el-form-item label="服务商 / 模型">
              <el-select v-model="form.statusProviderId" clearable placeholder="默认系统服务商" style="width: 200px" @change="loadModels('status')">
                <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
              <el-select v-model="form.statusModel" filterable allow-create clearable placeholder="模型（用最便宜的即可）" style="width: 240px; margin-left: 8px">
                <el-option v-for="m in models.status" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>

            <el-divider content-position="left">封面（由管理员统一设定）</el-divider>
            <el-form-item label="封面来源">
              <el-select v-model="form.coverSource" style="width: 240px">
                <el-option label="固定一张（推荐）" value="fixed" />
                <el-option label="封面池轮换（多张按顺序用）" value="pool" />
                <el-option label="随机 ACG 接口（质量不可控）" value="acg" />
                <el-option label="不要封面" value="none" />
              </el-select>
              <span class="tip">她写文章时按这里的设置取封面；建议用固定一张或自己的封面池（单张不超过 20MB，建议 2MB 以内）</span>
            </el-form-item>
            <el-form-item v-if="form.coverSource === 'fixed'" label="固定封面">
              <el-upload
                :action="'/api/admin/upload/image'"
                :headers="uploadHeaders"
                :show-file-list="false"
                accept="image/*"
                :on-success="onCoverSuccess"
              >
                <img v-if="form.coverFixed" :src="form.coverFixed" class="cover-preview" alt="" />
                <el-button v-else>上传封面图</el-button>
              </el-upload>
              <el-input v-model="form.coverFixed" placeholder="/uploads/..." style="width: 320px; margin-left: 8px" />
            </el-form-item>
            <el-form-item v-if="form.coverSource === 'pool'" label="封面池">
              <el-input
                v-model="form.coverPool"
                type="textarea"
                :rows="4"
                style="width: 620px"
                placeholder="每行一个图片地址，她发文时按顺序轮换使用"
              />
              <el-upload
                :action="'/api/admin/upload/image'"
                :headers="uploadHeaders"
                :show-file-list="false"
                accept="image/*"
                :on-success="onCoverPoolSuccess"
                style="margin-left: 8px"
              >
                <el-button>上传并追加一张</el-button>
              </el-upload>
            </el-form-item>
            <el-form-item label="已发布的文章">
              <el-button :loading="applyingCovers" @click="onApplyCovers">
                把已有文章的封面统一换成上面的设置
              </el-button>
              <span class="tip">之前用随机接口生成的封面质量不可控，改完设置点这里一键替换</span>
            </el-form-item>

            <el-divider content-position="left">每日运行情况</el-divider>
            <el-form-item label="每天写运行情况">
              <el-switch v-model="form.reportEnabled" :active-value="1" :inactive-value="0" />
              <el-input v-model="form.reportTime" style="width: 90px; margin-left: 12px" placeholder="23:00" />
              <span class="tip">每天这个时间以「今日运行情况」为题写一篇（正文以运行数据为主，含表格）</span>
            </el-form-item>
            <el-form-item label="参考运行数据">
              <el-switch v-model="form.digestEnabled" :active-value="1" :inactive-value="0" />
              <span class="tip">只对上面那篇「今日运行情况」生效：关闭则她写运行情况时不带数据（其余文章本来就不涉及数据）</span>
            </el-form-item>
            <el-form-item label="数据范围">
              <el-select v-model="form.digestScope" style="width: 260px">
                <el-option label="只给本站聚合数字" value="site" />
                <el-option label="再加上沙盒世界细节" value="site+sandbox" />
              </el-select>
              <span class="tip">都只给计数：不含用户昵称、邮箱、IP，也不含金额与模型名</span>
            </el-form-item>
            <el-form-item label="吐槽语气">
              <el-radio-group v-model="form.digestTone">
                <el-radio value="gentle">克制观察（推荐）</el-radio>
                <el-radio value="spicy">更毒舌一点</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="简报预览">
              <el-button @click="onPreviewDigest">看看她会读到什么</el-button>
              <span class="tip">按当前数据范围与时间实时生成，仅供核对</span>
            </el-form-item>            <el-form-item>
              <el-button type="primary" :loading="saving" @click="onSave">保存参数</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ================= 角色档案 ================= -->
        <el-tab-pane label="角色档案" name="profile">
          <el-form label-width="150px">
            <el-form-item label="显示名">
              <el-input v-model="form.nameCn" style="width: 160px" placeholder="伊莉丝" />
              <el-input v-model="form.nameEn" style="width: 160px; margin-left: 8px" placeholder="IRIS" />
              <span class="tip">前台展示为「中文名 英文名」；提示词与自称统一用英文名</span>
            </el-form-item>
            <el-form-item label="型号">
              <el-input v-model="form.modelNo" style="width: 160px" placeholder="IRIS" />
            </el-form-item>
            <el-form-item label="一句话介绍">
              <el-input v-model="form.tagline" maxlength="120" style="width: 520px" />
            </el-form-item>
            <el-form-item label="头像 / 立绘">
              <el-upload
                :action="'/api/admin/upload/image'"
                :headers="uploadHeaders"
                :show-file-list="false"
                accept="image/*"
                :on-success="onAvatarSuccess"
              >
                <img v-if="form.avatar" :src="form.avatar" class="avatar-preview" alt="" />
                <el-button v-else>上传图片</el-button>
              </el-upload>
              <el-button v-if="form.avatar" link type="danger" @click="form.avatar = ''">清除</el-button>
              <span class="tip">留空时前台用占位图（单张不超过 20MB，建议 2MB 以内加载更快）</span>
            </el-form-item>
            <el-form-item label="主页简介">
              <el-input v-model="form.bio" type="textarea" :rows="3" maxlength="1000" style="width: 620px" />
            </el-form-item>
            <el-form-item label="结构化档案">
              <el-input
                v-model="form.personalityJson"
                type="textarea"
                :rows="12"
                style="width: 760px"
                placeholder='建议用 JSON 存：{"外观":"...","性格":"...","口头禅":["正在执行。"],"禁忌":"...","说话风格":"..."}'
              />
              <div class="tip">这段会原样注入提示词，是她的"人设"。改完保存即刻生效，不用改代码。</div>
            </el-form-item>
            <el-form-item label="提示词补充">
              <el-input v-model="form.promptExtra" type="textarea" :rows="4" style="width: 760px" placeholder="例如：最近多写点关于雨和旧书的内容；不要提到政治话题" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="onSave">保存档案</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ================= 活动日志 ================= -->
        <el-tab-pane label="活动日志" name="activities">
          <div class="toolbar">
            <el-radio-group v-model="activityType" size="small" @change="loadActivities">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="article">写文章</el-radio-button>
              <el-radio-button value="comment">评论</el-radio-button>
              <el-radio-button value="reply">回复</el-radio-button>
              <el-radio-button value="status">状态</el-radio-button>
              <el-radio-button value="report">运行情况</el-radio-button>
              <el-radio-button value="memory">记忆</el-radio-button>
            </el-radio-group>
          </div>
          <el-table :data="activities" v-loading="loadingActivities" size="small" class="mt">
            <el-table-column prop="createTime" label="时间" width="160" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ typeText(row.activityType) }}</template>
            </el-table-column>
            <el-table-column prop="title" label="标题 / 目标" min-width="150" show-overflow-tooltip />
            <el-table-column label="内容" min-width="300">
              <template #default="{ row }">
                <div class="log-content">{{ row.content }}</div>
                <div v-if="row.error" class="muted">错误：{{ row.error }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="model" label="模型" width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag>
                <el-tag v-if="row.reverted === 1" size="small" type="info" class="ml">已撤销</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.revertible === 1 && row.reverted !== 1"
                  size="small"
                  type="danger"
                  link
                  @click="onRevert(row)"
                >撤销</el-button>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="activityTotal > activitySize"
            v-model:current-page="activityPage"
            :page-size="activitySize"
            :total="activityTotal"
            layout="prev, pager, next"
            class="pager"
            @current-change="loadActivities"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  siteAiOverview,
  siteAiDigest,
  resetSiteAiCounters,
  applySiteAiCovers,
  siteAiProfile,
  saveSiteAiProfile,
  siteAiActivities,
  runSiteAi,
  revertSiteAiActivity,
  summarizeSiteAi
} from '@/api/siteAi'
import { aiProviderList, aiProviderModels } from '@/api/ai'

const uploadHeaders = { Authorization: localStorage.getItem('token') || '' }
const applyingCovers = ref(false)
const resetting = ref(false)
const digestVisible = ref(false)
const digestText = ref('')

const tab = ref('overview')
const saving = ref(false)
const running = ref('')
const overview = ref({})
const providers = ref([])
const models = reactive({ article: [], comment: [], reply: [], status: [] })

const form = reactive({
  enabled: 0,
  sensitiveFilterEnabled: 1,
  nameEn: 'IRIS',
  nameCn: '伊莉丝',
  modelNo: 'IRIS',
  tagline: '',
  avatar: '',
  bio: '',
  personalityJson: '',
  promptExtra: '',
  dailyLimit: 6,
  articleEnabled: 1,
  articleDailyLimit: 1,
  articleWindowStart: '09:00',
  articleWindowEnd: '22:00',
  articleRandom: 1,
  articleTime: '09:00',
  articleProviderId: null,
  articleModel: '',
  articleTopics: '',
  articleAvoid: '',
  commentEnabled: 1,
  commentTime: '15:00',
  commentProviderId: null,
  commentModel: '',
  commentScope: 'latest+owner',
  musingTime: '21:00',
  replyEnabled: 1,
  replyProviderId: null,
  replyModel: '',
  replyCooldownMinutes: 30,
  replyDailyLimit: 5,
  statusProviderId: null,
  statusModel: '',
  coverSource: 'acg',
  coverFixed: '',
  coverPool: '',
  memoryDays: 7,
  digestEnabled: 1,
  digestScope: 'site',
  digestTone: 'gentle',
  reportEnabled: 1,
  reportTime: '23:00'
})

const activities = ref([])
const activityType = ref('')
const activityPage = ref(1)
const activitySize = 10
const activityTotal = ref(0)
const loadingActivities = ref(false)

async function loadProfile() {
  const p = await siteAiProfile()
  if (p) {
    Object.keys(form).forEach((k) => {
      if (p[k] !== undefined && p[k] !== null) {
        form[k] = p[k]
      }
    })
  }
}

async function loadOverview() {
  overview.value = (await siteAiOverview()) || {}
}

async function loadActivities() {
  loadingActivities.value = true
  try {
    const data = await siteAiActivities({
      type: activityType.value || undefined,
      page: activityPage.value,
      size: activitySize
    })
    activities.value = data.list || []
    activityTotal.value = data.total || 0
  } finally {
    loadingActivities.value = false
  }
}

async function loadAll() {
  await loadProfile()
  await loadOverview()
  await loadActivities()
}

async function loadModels(kind) {
  const map = { article: 'articleProviderId', comment: 'commentProviderId', reply: 'replyProviderId', status: 'statusProviderId' }
  const pid = form[map[kind]]
  if (!pid) {
    ElMessage.warning('先选择服务商（留空表示用系统服务商）')
    return
  }
  models[kind] = (await aiProviderModels(Number(pid))) || []
  if (!models[kind].length) {
    ElMessage.warning('没取到模型，可以手填模型名')
  }
}

async function onSave() {
  saving.value = true
  try {
    await saveSiteAiProfile({ ...form })
    ElMessage.success('已保存')
    await loadProfile()
    await loadOverview()
  } finally {
    saving.value = false
  }
}

async function onRun(type) {
  running.value = type
  try {
    const a = await runSiteAi(type)
    if (a && a.status === 'success') {
      ElMessage.success('执行成功：' + typeText(type))
    } else if (a && a.status === 'blocked') {
      ElMessage.warning('本次内容被敏感词拦下，没有发布')
    } else if (a && a.status === 'skipped') {
      ElMessage.info(a.error || '本次跳过')
    } else {
      ElMessage.warning('执行完成，但结果异常，请看活动日志')
    }
    await loadAll()
  } finally {
    running.value = ''
  }
}

async function onMemory() {
  running.value = 'memory'
  try {
    const n = await summarizeSiteAi()
    ElMessage.success(n > 0 ? '今天的记忆已生成' : '今天还没有活动，暂不需要总结')
    await loadAll()
  } finally {
    running.value = ''
  }
}

async function onRevert(row) {
  await ElMessageBox.confirm(
    row.activityType === 'article' ? '撤销后这篇文章会从前台下架（评论则直接删除），确定吗？' : '撤销后会删除她发的这条评论，确定吗？',
    '撤销',
    { type: 'warning' }
  )
  await revertSiteAiActivity(row.id)
  ElMessage.success('已撤销')
  await loadAll()
}

function onAvatarSuccess(res) {
  if (res && res.code === 200 && res.data) {
    form.avatar = res.data
    ElMessage.success('已上传，记得点保存')
  } else {
    ElMessage.error((res && res.message) || '上传失败')
  }
}

async function onResetCounters() {
  resetting.value = true
  try {
    await resetSiteAiCounters()
    ElMessage.success('今日计数已重置，可以继续测试')
    await loadOverview()
  } finally {
    resetting.value = false
  }
}

async function onPreviewDigest() {
  digestText.value = await siteAiDigest()
  digestVisible.value = true
}

async function onApplyCovers() {
  applyingCovers.value = true
  try {
    const n = await applySiteAiCovers()
    ElMessage.success('已把 ' + n + ' 篇文章的封面换成当前设置')
    await loadOverview()
  } finally {
    applyingCovers.value = false
  }
}

function onCoverSuccess(res) {
  if (res && res.code === 200 && res.data) {
    form.coverFixed = res.data
    ElMessage.success('已上传，记得点保存')
  } else {
    ElMessage.error((res && res.message) || '上传失败')
  }
}

function onCoverPoolSuccess(res) {
  if (res && res.code === 200 && res.data) {
    form.coverPool = (form.coverPool ? form.coverPool.replace(/\s+$/, '') + '\n' : '') + res.data
    ElMessage.success('已追加到封面池，记得点保存')
  } else {
    ElMessage.error((res && res.message) || '上传失败')
  }
}

function typeText(type) {
  if (type === 'article') return '写文章'
  if (type === 'comment') return '评论'
  if (type === 'reply') return '回复'
  if (type === 'status') return '状态'
  if (type === 'report') return '运行情况'
  if (type === 'memory') return '记忆'
  return type
}

function statusType(status) {
  if (status === 'success') return 'success'
  if (status === 'blocked') return 'danger'
  if (status === 'failed') return 'warning'
  return 'info'
}

onMounted(async () => {
  providers.value = (await aiProviderList()) || []
  await loadAll()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.title {
  font-weight: 700;
}
.stats {
  margin-bottom: 8px;
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}
.stat .num {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-color-primary);
}
.stat .lbl {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.status-line {
  margin: 10px 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 6px;
}
.tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.log-content {
  white-space: pre-wrap;
  word-break: break-word;
}
.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ml {
  margin-left: 6px;
}
.mt {
  margin-top: 12px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.digest-box {
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
.cover-preview {
  width: 96px;
  height: 54px;
  object-fit: cover;
  border-radius: 8px;
  display: block;
}
.avatar-preview {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 10px;
  display: block;
}
.setting-form .el-form-item {
  margin-bottom: 14px;
}
</style>
