<template>
  <div class="release-manager" v-loading="loading">
    <el-alert title="支持3 GiB分片续传。新平台首次上传为草稿；更新已发布平台会自动替换线上版本。校验成功后删除旧包，失败则保留旧版。" type="info" :closable="false" show-icon />
    <el-card v-for="platform in platforms" :key="platform.id">
      <template #header><div class="release-card-head"><div><strong>{{ platform.name }}</strong><span>{{ platform.hint }}</span></div><el-tag :type="packages[platform.id]?.published ? 'success' : 'info'">{{ packages[platform.id]?.published ? '已发布' : '未发布' }}</el-tag></div></template>
      <div v-if="packages[platform.id]" class="current-package"><div><strong>{{ packages[platform.id].version }}</strong> · {{ formatPackageSize(packages[platform.id].size) }}</div><p>{{ packages[platform.id].fileName }}</p><p class="hash">SHA-256：{{ packages[platform.id].sha256 }}</p><el-button :disabled="!!busyPlatform" :type="packages[platform.id].published ? 'warning' : 'primary'" @click="setPublished(platform.id)">{{ packages[platform.id].published ? '下架下载' : '发布下载' }}</el-button></div>
      <el-form label-position="top" class="package-form">
        <el-form-item label="安装包版本"><el-input v-model="forms[platform.id].version" maxlength="40" placeholder="例如 V0.55" :disabled="busyPlatform === platform.id" /></el-form-item>
        <el-form-item label="更新说明"><el-input v-model="forms[platform.id].notes" type="textarea" :rows="3" maxlength="4000" show-word-limit :disabled="busyPlatform === platform.id" /></el-form-item>
        <el-form-item label="选择安装包"><input type="file" :accept="platform.accept" :disabled="!!busyPlatform" @change="selectFile(platform.id, $event)" /><span v-if="forms[platform.id].file" class="file-size">{{ formatPackageSize(forms[platform.id].file.size) }}</span></el-form-item>
        <div v-if="progressPlatform === platform.id" role="status" class="upload-status"><el-progress :percentage="progress" :stroke-width="8" /><p>{{ phase }}</p><p v-if="busyPlatform === platform.id && !merging">{{ transferred }} / {{ formatPackageSize(forms[platform.id].file?.size || 0) }} · {{ speed }}</p></div>
        <div class="upload-actions"><el-button type="primary" :disabled="!!busyPlatform || !forms[platform.id].file || !forms[platform.id].version.trim()" @click="upload(platform.id)">上传 / 继续上传</el-button><el-button v-if="busyPlatform === platform.id && !merging" @click="pauseUpload">暂停上传</el-button></div>
      </el-form>
      <p class="release-tip">暂停或刷新后重新选择同一文件可续传，24小时未活动的分片自动清理。合并时需要约“新包大小×2＋旧包大小”的峰值空间；新版完成后自动回收旧包。</p>
    </el-card>
    <router-link to="/portal/games/pixelrogue#game-release" target="_blank">查看前台下载区 ↗</router-link>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminGamePackages, publishGamePackage, startGameUpload, gameUploadStatus, uploadGameChunk, completeGameUpload } from '@/api/gamePackages'
import { formatPackageSize, GAME_PACKAGE_MAX_SIZE } from '@/utils/gamePackages'
const platforms = [{ id: 'android', name: 'Android 安卓版', hint: 'APK 安装包', accept: '.apk' }, { id: 'pc', name: 'Windows PC版', hint: 'EXE 安装程序 / ZIP 压缩包', accept: '.exe,.zip' }]
const forms = reactive({ android: { version: '', notes: '', file: null }, pc: { version: '', notes: '', file: null } })
const packages = ref({})
const loading = ref(false)
const busyPlatform = ref('')
const progress = ref(0)
const phase = ref('')
const merging = ref(false)
const progressPlatform = ref('')
const transferred = ref('0 MiB')
const speed = ref('计算上传速度…')
let controller
let paused = false
let disposed = false
function selectFile(platform, event) {
  const file = event.target.files?.[0]
  if (!file) { forms[platform].file = null; return }
  if (!file.size || file.size > GAME_PACKAGE_MAX_SIZE || !(platform === 'android' ? /\.apk$/i : /\.(exe|zip)$/i).test(file.name)) {
    ElMessage.error('请选择对应平台且不超过3 GiB的安装包'); event.target.value = ''; forms[platform].file = null; return
  }
  forms[platform].file = file
}
async function load() {
  loading.value = true
  try { const data = await adminGamePackages(); if (!disposed) packages.value = Object.fromEntries(data.map((item) => [item.platform, item])) } catch { /* Request interceptor shows the error. */ }
  finally { loading.value = false }
}
async function setPublished(platform) {
  busyPlatform.value = platform
  try { packages.value[platform] = await publishGamePackage(platform, !packages.value[platform].published); ElMessage.success(packages.value[platform].published ? '下载已发布' : '下载已下架') } catch { /* Request interceptor shows the error. */ }
  finally { busyPlatform.value = '' }
}
function pauseUpload() { paused = true; controller?.abort() }
async function upload(platform) {
  const form = forms[platform], file = form.file
  if (!file || busyPlatform.value) return
  const key = `bcblog-game-upload-${platform}`
  const fingerprint = JSON.stringify([file.name, file.size, file.lastModified, form.version.trim(), form.notes])
  busyPlatform.value = platform; progressPlatform.value = platform; progress.value = 0; paused = false; controller = new AbortController()
  try {
    let session
    try {
      const saved = JSON.parse(localStorage.getItem(key) || 'null')
      if (saved?.fingerprint === fingerprint) session = await gameUploadStatus(saved.id)
    } catch { /* Missing sessions restart; already accepted chunks are never overwritten in another session. */ }
    if (paused || disposed) return
    if (!session) {
      session = await startGameUpload({ platform, fileName: file.name, size: file.size, version: form.version.trim(), notes: form.notes })
      try { localStorage.setItem(key, JSON.stringify({ fingerprint, id: session.id })) } catch { /* Continue without persistent resume. */ }
    }
    const done = new Set(session.uploaded)
    let sent = [...done].reduce((sum, index) => sum + Math.min(session.chunkSize, file.size - index * session.chunkSize), 0)
    const initialSent = sent, startedAt = performance.now()
    progress.value = Math.min(99, Math.floor(sent / file.size * 100))
    transferred.value = formatPackageSize(sent)
    const count = Math.ceil(file.size / session.chunkSize)
    for (let index = 0; index < count && !session.result; index++) {
      if (paused || disposed) return
      if (done.has(index)) continue
      phase.value = `上传分片 ${index + 1} / ${count}，可随时暂停`
      const chunk = file.slice(index * session.chunkSize, (index + 1) * session.chunkSize)
      await uploadGameChunk(session.id, index, chunk, (loaded) => {
        const total = sent + Math.min(loaded, chunk.size)
        progress.value = Math.min(99, Math.floor(total / file.size * 100))
        transferred.value = formatPackageSize(total)
        speed.value = `${formatPackageSize((total - initialSent) / Math.max(.1, (performance.now() - startedAt) / 1000))}/s`
      }, controller.signal)
      sent += chunk.size
    }
    if (paused || disposed) return
    merging.value = true; progress.value = 99; phase.value = '正在合并与校验，请勿关闭页面…'
    const result = session.result || await completeGameUpload(session.id)
    packages.value[platform] = result; progress.value = 100; phase.value = result.published ? '新版已替换并发布，旧包已自动清理。' : '上传完成，点击“发布下载”后对访客可见。'
    try { localStorage.removeItem(key) } catch { /* Ignore unavailable storage. */ }
    ElMessage.success(result.published ? '新版安装包已替换发布' : '安装包已保存为未发布草稿')
  } catch (error) { if (!paused && !disposed) phase.value = '上传中断，重新选择同一文件并点击继续上传。' }
  finally { if (paused && !disposed) phase.value = '已暂停，点击继续上传可从已完成的分片恢复。'; merging.value = false; busyPlatform.value = '' }
}
onMounted(load)
onUnmounted(() => { disposed = true; pauseUpload() })
</script>
<style scoped>
.release-manager { display: grid; gap: 24px; }
.release-card-head { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.release-card-head strong { display: block; font-size: 16px; }
.release-card-head span:not(.el-tag) { display: block; color: var(--admin-muted); font-size: 11px; margin-top: 6px; }
.current-package { padding: 18px; background: #f7f5fb; border-radius: 12px; margin-bottom: 22px; font-size: 13px; }
.current-package p { overflow-wrap: anywhere; color: var(--admin-muted); line-height: 1.8; font-size: 12px; }
.current-package .hash { font-family: Consolas, monospace; font-size: 11px; }
.package-form { max-width: 820px; }
.file-size { margin-left: 12px; font-size: 11px; color: var(--admin-muted); }
.release-tip { font-size: 11px; line-height: 1.9; color: var(--admin-muted); margin-top: 20px; }
.upload-actions { display: flex; gap: 12px; }
.upload-status p { font-size: 12px; color: var(--admin-muted); }
input[type=file] { max-width: 100%; font-size: 13px; }
</style>
