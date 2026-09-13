<template>
  <div class="music glass" :class="{ open }">
    <button class="fab" @click="open = !open">
      <span v-if="playing" class="eq"><i></i><i></i><i></i></span>
      <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M9 18V5l12-2v13" />
        <circle cx="6" cy="18" r="3" />
        <circle cx="18" cy="16" r="3" />
      </svg>
    </button>

    <transition name="pop">
      <div v-if="open" class="panel">
        <div class="head">
          <span class="label">音乐</span>
          <button class="close" @click="open = false">×</button>
        </div>

        <div class="now">
          <div
            class="disc"
            :class="{ spin: playing }"
            :style="current.pic ? { backgroundImage: `url(${current.pic})` } : {}"
          ></div>
          <div class="meta">
            <div class="title">{{ current.title || '未在播放' }}</div>
            <div class="artist">{{ current.artist || '—' }}</div>
          </div>
        </div>

        <div class="progress" @click="seek">
          <div class="bar"><div class="fill" :style="{ width: progress + '%' }"></div></div>
          <div class="times">
            <span>{{ fmt(currentTime) }}</span>
            <span>{{ fmt(duration) }}</span>
          </div>
        </div>

        <div class="controls">
          <button @click="prev"><svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z" /></svg></button>
          <button class="play" @click="toggle">
            <svg v-if="!playing" viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
            <svg v-else viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><path d="M6 5h4v14H6zm8 0h4v14h-4z" /></svg>
          </button>
          <button @click="next"><svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M16 6h2v12h-2zM6 18l8.5-6L6 6z" /></svg></button>
        </div>

        <div class="volume">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 5 6 9H2v6h4l5 4zM15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13" /></svg>
          <input v-model="volume" type="range" min="0" max="1" step="0.01" @input="setVolume" />
        </div>

        <div v-if="loading" class="loading">歌单加载中...</div>
        <ul v-else class="list">
          <li
            v-for="(s, i) in songs"
            :key="s.src"
            :class="{ active: i === index }"
            @click="play(i)"
          >
            <span class="name">{{ s.title }}</span>
            <span class="artist">{{ s.artist }}</span>
          </li>
          <li v-if="!songs.length" class="empty">暂无歌曲</li>
        </ul>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { getActivePlaylist } from '@/api/music'

const open = ref(false)
const playing = ref(false)
const index = ref(0)
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(0.6)
const loading = ref(false)
const songs = ref([])
let audio = null
let errorStreak = 0

// 网易云歌单 ID：参考 E:\blog 项目使用 APlayer + Meting 的方式接入。
// 换歌单时，替换下面这个 ID 即可（网易云歌单地址里的 id）。
const PLAYLIST_ID = '18381082288'

// Meting 接口失败时的兜底歌单（免密钥外链，仅可播放非版权受限歌曲）
const fallback = [
  { title: '起风了', artist: '买辣椒也用券', src: 'https://music.163.com/song/media/outer/url?id=1330348068.mp3', pic: '' },
  { title: '少年', artist: 'Dave', src: 'https://music.163.com/song/media/outer/url?id=2614935159.mp3', pic: '' },
  { title: '卡农（经典钢琴版）', artist: 'dylanf', src: 'https://music.163.com/song/media/outer/url?id=478507889.mp3', pic: '' },
  { title: '七点钟', artist: '齐豫', src: 'https://music.163.com/song/media/outer/url?id=108787.mp3', pic: '' }
]

const current = computed(() => songs.value[index.value] || songs.value[0] || {})
const progress = computed(() => (duration.value ? (currentTime.value / duration.value) * 100 : 0))

function fmt(s) {
  if (!s || !isFinite(s)) return '00:00'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

async function loadPlaylist() {
  loading.value = true
  try {
    const pid = await resolvePlaylistId()
    const res = await fetch(`https://api.i-meto.com/meting/api?server=netease&type=playlist&id=${pid}`)
    const data = await res.json()
    const arr = Array.isArray(data) ? data : []
    songs.value = arr
      .map((s) => ({
        title: s.title || s.name || '未知歌曲',
        artist: s.author || s.artist || '未知歌手',
        src: s.url || '',
        pic: s.pic || ''
      }))
      .filter((s) => s.src)
  } catch (e) {
    songs.value = fallback
  } finally {
    loading.value = false
  }
}

// 读取后台当前启用的歌单 ID，读不到时用默认歌单
async function resolvePlaylistId() {
  try {
    const id = await getActivePlaylist()
    return id || PLAYLIST_ID
  } catch (e) {
    return PLAYLIST_ID
  }
}

// 打开页面自动播放；若被浏览器拦截，则在首次交互时补播
async function tryAutoplay() {
  if (!audio || !songs.value.length) return
  audio.src = songs.value[0].src
  try {
    await audio.play()
  } catch (e) {
    const cleanup = () => {
      window.removeEventListener('pointerdown', retry)
      window.removeEventListener('wheel', retry)
      window.removeEventListener('keydown', retry)
      window.removeEventListener('touchstart', retry)
    }
    const retry = () => {
      if (audio) audio.play().catch(() => {})
      cleanup()
    }
    window.addEventListener('pointerdown', retry)
    window.addEventListener('wheel', retry)
    window.addEventListener('keydown', retry)
    window.addEventListener('touchstart', retry)
  }
}

function bindAudio() {
  if (!audio) return
  audio.volume = volume.value
  audio.addEventListener('timeupdate', () => {
    currentTime.value = audio.currentTime
    duration.value = audio.duration || 0
  })
  audio.addEventListener('ended', next)
  audio.addEventListener('play', () => {
    errorStreak = 0
    playing.value = true
  })
  audio.addEventListener('pause', () => (playing.value = false))
  audio.addEventListener('error', () => {
    playing.value = false
    errorStreak++
    // 连续失败次数超过歌单长度时停止，避免版权受限歌曲过多导致无限跳过
    if (errorStreak >= songs.value.length) {
      errorStreak = 0
      return
    }
    next()
  })
}

function play(i) {
  if (!audio || !songs.value.length) return
  if (i === index.value && audio.src) {
    toggle()
    return
  }
  index.value = i
  audio.src = songs.value[i].src
  audio.play().catch(() => {})
}

function toggle() {
  if (!audio) return
  if (!audio.src) {
    play(0)
    return
  }
  if (audio.paused) audio.play().catch(() => {})
  else audio.pause()
}

function next() {
  if (!songs.value.length) return
  play((index.value + 1) % songs.value.length)
}

function prev() {
  if (!songs.value.length) return
  play((index.value - 1 + songs.value.length) % songs.value.length)
}

function seek(e) {
  if (!audio || !duration.value) return
  const rect = e.currentTarget.getBoundingClientRect()
  const ratio = (e.clientX - rect.left) / rect.width
  audio.currentTime = ratio * duration.value
}

function setVolume() {
  if (audio) audio.volume = volume.value
}

onMounted(() => {
  audio = new Audio()
  bindAudio()
  loadPlaylist().then(tryAutoplay)
})

onUnmounted(() => {
  if (audio) {
    audio.pause()
    audio = null
  }
})
</script>

<style scoped>
.music {
  position: fixed;
  left: 18px;
  bottom: 18px;
  z-index: 60;
  border-radius: 22px;
  box-shadow: var(--shadow);
}
.fab {
  width: 46px;
  height: 46px;
  border: none;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, var(--accent-2), var(--accent));
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow);
}
.eq {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 16px;
}
.eq i {
  width: 3px;
  background: #fff;
  border-radius: 2px;
  animation: bounce 0.8s ease-in-out infinite;
}
.eq i:nth-child(1) { height: 10px; }
.eq i:nth-child(2) { height: 16px; animation-delay: 0.15s; }
.eq i:nth-child(3) { height: 8px; animation-delay: 0.3s; }
@keyframes bounce {
  0%, 100% { transform: scaleY(0.4); }
  50% { transform: scaleY(1); }
}
.panel {
  width: 300px;
  padding: 14px 16px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.label {
  font-size: 13px;
  color: var(--text-muted);
  letter-spacing: 1px;
}
.close {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 20px;
  cursor: pointer;
}
.now {
  display: flex;
  align-items: center;
  gap: 12px;
}
.disc {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, #ffd3e2, var(--accent) 60%, var(--accent-2));
  background-size: cover;
  background-position: center;
  box-shadow: var(--shadow);
  flex-shrink: 0;
}
.disc.spin {
  animation: rotate 8s linear infinite;
}
@keyframes rotate {
  to { transform: rotate(360deg); }
}
.meta {
  min-width: 0;
}
.title {
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.artist {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}
.progress {
  margin: 14px 0 10px;
}
.bar {
  height: 5px;
  background: var(--accent-soft);
  border-radius: 4px;
  cursor: pointer;
}
.fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent), var(--accent-2));
  border-radius: 4px;
}
.times {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}
.controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-bottom: 12px;
}
.controls button {
  border: none;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  display: flex;
}
.controls .play {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  color: #fff;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow);
}
.volume {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  margin-bottom: 12px;
}
.volume input {
  flex: 1;
  accent-color: var(--accent);
}
.loading,
.empty {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  padding: 12px 0;
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 180px;
  overflow-y: auto;
}
.list li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}
.list li:hover {
  background: var(--accent-soft);
}
.list li.active {
  background: var(--accent-soft);
  color: var(--accent);
}
.list .name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.list .artist {
  color: var(--text-muted);
  flex-shrink: 0;
}
.pop-enter-active,
.pop-leave-active {
  transition: all 0.22s ease;
}
.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.96);
}
@media (max-width: 768px) {
  .music {
    left: 12px;
    bottom: 12px;
  }
  .panel {
    width: 260px;
  }
}
</style>
