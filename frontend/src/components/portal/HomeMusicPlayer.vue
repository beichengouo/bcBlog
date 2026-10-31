<template>
  <div class="home-music">
    <div class="player-card glass">
      <div
        class="cover"
        :class="{ spin: playing }"
        :style="current.pic ? { backgroundImage: `url(${current.pic})` } : {}"
      >
        <span class="cover-hole"></span>
      </div>

      <div class="info">
        <span class="tag">NOW PLAYING</span>
        <h3 class="song-title">{{ current.title || '暂无歌曲' }}</h3>
        <p class="song-artist">{{ current.artist || '—' }}</p>
      </div>

      <div class="progress" @click="seek">
        <div class="bar"><div class="fill" :style="{ width: progress + '%' }"></div></div>
        <div class="times">
          <span>{{ fmt(currentTime) }}</span>
          <span>{{ fmt(duration) }}</span>
        </div>
      </div>

      <div class="controls">
        <button @click="prev" aria-label="上一首">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z" /></svg>
        </button>
        <button class="play" @click="toggle" aria-label="播放/暂停">
          <svg v-if="!playing" viewBox="0 0 24 24" width="26" height="26" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
          <svg v-else viewBox="0 0 24 24" width="26" height="26" fill="currentColor"><path d="M6 5h4v14H6zm8 0h4v14h-4z" /></svg>
        </button>
        <button @click="next" aria-label="下一首">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M16 6h2v12h-2zM6 18l8.5-6L6 6z" /></svg>
        </button>
      </div>
    </div>

    <section class="lyric-panel glass" aria-label="同步歌词">
      <header class="lyric-heading"><span>歌词 <small>LYRICS</small></span><span class="wave" :class="{ active: playing }" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></span></header>
      <div ref="lyricViewport" class="lyric-viewport" tabindex="0" aria-label="歌词列表，可滚动查看">
        <div v-if="lyricLines.length" ref="lyricTrack" class="lyric-track">
          <p v-for="(line, index) in lyricLines" :key="line.time" class="lyric-row" :class="{ 'is-current': index === activeLine, 'is-near': Math.abs(index - activeLine) === 1 }" :aria-current="index === activeLine ? 'true' : undefined" :data-lyric-index="index">{{ line.text }}</p>
        </div>
        <div v-else class="lyric-empty"><span>♪</span><p>{{ current.title || '等待音乐响起' }}</p><small>暂无可用歌词，静静听一会儿吧</small></div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch, onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useMusicStore } from '@/store/music'

const music = useMusicStore()
const { current, progress, playing, currentTime, duration, lyrics } = storeToRefs(music)
const { prev, toggle, next } = music

const lyricViewport = ref(null), lyricTrack = ref(null)
let lyricResizeObserver
// Keep translations sharing a timestamp with their original line.
const lyricLines = computed(() => {
  const lines = []
  for (const line of lyrics.value) {
    const previous = lines[lines.length - 1]
    if (previous && previous.time === line.time) previous.text += '\n' + line.text
    else lines.push({ ...line })
  }
  return lines
})
const activeLine = computed(() => {
  let index = -1
  for (let i = 0; i < lyricLines.value.length && lyricLines.value[i].time <= currentTime.value; i++) index = i
  return index
})
async function centerLyric(smooth = true) {
  await nextTick()
  const viewport = lyricViewport.value
  if (!viewport) return
  const row = viewport.querySelector(`[data-lyric-index="${Math.max(0, activeLine.value)}"]`)
  const top = row ? Math.max(0, row.offsetTop + row.offsetHeight / 2 - viewport.clientHeight / 2) : 0
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  viewport.scrollTo({ top, behavior: smooth && !reduced ? 'smooth' : 'auto' })
}
watch(activeLine, () => centerLyric())
watch(lyricLines, () => centerLyric(false), { flush: 'post' })
watch(lyricTrack, (track, oldTrack) => {
  if (oldTrack) lyricResizeObserver?.unobserve(oldTrack)
  if (track) lyricResizeObserver?.observe(track)
})

function fmt(s) {
  if (!s || !isFinite(s)) return '00:00'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

function seek(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  music.seekRatio((e.clientX - rect.left) / rect.width)
}

onMounted(() => {
  music.init()
  lyricResizeObserver = new ResizeObserver(() => centerLyric(false))
  lyricResizeObserver.observe(lyricViewport.value)
  if (lyricTrack.value) lyricResizeObserver.observe(lyricTrack.value)
  centerLyric(false)
})
onUnmounted(() => lyricResizeObserver?.disconnect())
</script>

<style scoped>
.home-music {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
}
.player-card {
  position: relative;
  flex: 1;
  min-height: 300px;
  padding: 24px;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  overflow: hidden;
}
.player-card::before {
  content: '';
  position: absolute;
  top: -60px;
  right: -60px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: var(--accent-soft);
  filter: blur(10px);
}
.cover {
  position: relative;
  width: 108px;
  height: 108px;
  border-radius: 50%;
  background: radial-gradient(circle at 32% 32%, #ffd3e2, var(--accent) 62%, var(--accent-2));
  background-size: cover;
  background-position: center;
  box-shadow: var(--shadow);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}
.cover.spin {
  animation: rotate 9s linear infinite;
}
.cover-hole {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--card-solid);
  box-shadow: inset 0 0 4px rgba(0, 0, 0, 0.3);
}
@keyframes rotate {
  to { transform: rotate(360deg); }
}
.info {
  text-align: center;
  z-index: 1;
  max-width: 100%;
}
.tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 11px;
  letter-spacing: 1px;
}
.song-title {
  margin: 8px 0 4px;
  font-size: 18px;
  color: var(--text-strong);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.song-artist {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
}
.progress {
  width: 100%;
  z-index: 1;
}
.bar {
  height: 5px;
  border-radius: 4px;
  background: var(--accent-soft);
  cursor: pointer;
}
.fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--accent), var(--accent-2));
}
.times {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-muted);
}
.controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 22px;
  z-index: 1;
}
.controls button {
  border: none;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  display: flex;
  transition: transform 0.2s ease;
}
.controls button:hover {
  transform: scale(1.12);
}
.controls .play {
  width: 54px;
  height: 54px;
  border-radius: 50%;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  box-shadow: var(--shadow);
}

/* 歌词栏 */
.lyric-panel { flex-shrink: 0; padding: 18px 20px 8px; border-radius: 18px; box-shadow: var(--shadow); min-width: 0; }
.lyric-heading { display: flex; align-items: center; justify-content: space-between; color: var(--text-strong); font-size: 13px; font-weight: 600; }
.lyric-heading small { margin-left: 8px; font-size: 9px; letter-spacing: 2px; font-weight: 400; color: var(--text-muted); }
.lyric-viewport { position: relative; height: 260px; overflow-y: auto; overscroll-behavior: contain; scrollbar-width: none; mask-image: linear-gradient(transparent, #000 20%, #000 80%, transparent); -webkit-mask-image: linear-gradient(transparent, #000 20%, #000 80%, transparent); }
.lyric-viewport::-webkit-scrollbar { display: none; }
.lyric-viewport:focus-visible { outline: 1px solid var(--accent); outline-offset: -2px; border-radius: 8px; }
.lyric-track { position: relative; padding: 110px 5px; }
.lyric-row { margin: 0; min-height: 48px; box-sizing: border-box; display: flex; align-items: center; justify-content: center; padding: 12px 4px; white-space: pre-line; overflow-wrap: anywhere; text-align: center; color: var(--text-muted); opacity: .42; font-size: 14px; line-height: 1.8; transition: opacity .35s ease, color .35s ease, transform .35s ease; }
.lyric-row.is-near { opacity: .7; }
.lyric-row.is-current { opacity: 1; color: var(--text-strong); font-weight: 650; transform: scale(1.035); text-shadow: 0 0 20px var(--accent-soft); }
.lyric-empty { min-height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; color: var(--text-muted); }
.lyric-empty > span { color: var(--accent); font-size: 28px; }.lyric-empty p { font-size: 14px; margin: 12px 0; }.lyric-empty small { font-size: 11px; }
.wave {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 26px;
  flex-shrink: 0;
}
.wave i {
  width: 3px;
  height: 6px;
  border-radius: 3px;
  background: var(--accent);
}
.wave.active i {
  animation: wave 1s ease-in-out infinite;
}
.wave.active i:nth-child(1) { animation-delay: 0ms; }
.wave.active i:nth-child(2) { animation-delay: 150ms; }
.wave.active i:nth-child(3) { animation-delay: 300ms; }
.wave.active i:nth-child(4) { animation-delay: 200ms; }
.wave.active i:nth-child(5) { animation-delay: 100ms; }
@keyframes wave {
  0%, 100% { height: 6px; }
  50% { height: 24px; }
}
@media (prefers-reduced-motion: reduce) { .lyric-row { transition: none; }.cover.spin,.wave.active i { animation: none; } }
@media (max-width: 560px) {
  .lyric-viewport { height: 220px; }
  .lyric-track { padding-block: 90px; }
  .lyric-panel { padding-inline: 16px; }
  .player-card {
    min-height: 260px;
    padding: 18px;
  }
  .cover {
    width: 88px;
    height: 88px;
  }
}
</style>
