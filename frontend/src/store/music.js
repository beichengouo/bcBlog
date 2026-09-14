import { defineStore } from 'pinia'
import { getActivePlaylist, getFallbackSongs } from '@/api/music'

// 网易云歌单 ID（后台未配置启用歌单时使用）
const DEFAULT_PLAYLIST_ID = '18381082288'

// 后端默认歌曲也获取失败时的最后兜底
const BUILTIN_FALLBACK = [
  { title: '起风了', artist: '买辣椒也用券', src: 'https://music.163.com/song/media/outer/url?id=1330348068.mp3', pic: '', lrc: '' },
  { title: '少年', artist: 'Dave', src: 'https://music.163.com/song/media/outer/url?id=2614935159.mp3', pic: '', lrc: '' },
  { title: '卡农（经典钢琴版）', artist: 'dylanf', src: 'https://music.163.com/song/media/outer/url?id=478507889.mp3', pic: '', lrc: '' },
  { title: '七点钟', artist: '齐豫', src: 'https://music.163.com/song/media/outer/url?id=108787.mp3', pic: '', lrc: '' }
]

// Audio 对象不放进 Pinia state，避免被响应式代理包裹
let audio = null
let errorStreak = 0

/** 解析 LRC 歌词文本为 [{ time, text }] */
function parseLrc(raw) {
  if (!raw) return []
  const result = []
  const lines = String(raw).split('\n')
  for (const line of lines) {
    const timeExp = /\[(\d{2,}):(\d{2})(?:[.:](\d{2,3}))?\]/g
    const text = line.replace(/\[\d{2,}:\d{2}(?:[.:]\d{2,3})?\]/g, '').trim()
    if (!text) continue
    const matches = []
    let m
    while ((m = timeExp.exec(line)) !== null) {
      const min = parseInt(m[1], 10)
      const sec = parseInt(m[2], 10)
      const ms = m[3] ? parseFloat('0.' + m[3]) : 0
      matches.push(min * 60 + sec + ms)
    }
    matches.forEach((time) => result.push({ time, text }))
  }
  result.sort((a, b) => a.time - b.time)
  return result
}

export const useMusicStore = defineStore('music', {
  state: () => ({
    songs: [],
    index: 0,
    playing: false,
    currentTime: 0,
    duration: 0,
    volume: 0.6,
    loading: false,
    initialized: false,
    lyrics: [],
    currentLyric: ''
  }),
  getters: {
    current: (state) => state.songs[state.index] || state.songs[0] || {},
    progress: (state) => (state.duration ? (state.currentTime / state.duration) * 100 : 0)
  },
  actions: {
    /** 全局只初始化一次，多个播放器组件共用 */
    async init() {
      if (this.initialized) return
      this.initialized = true
      audio = new Audio()
      this.bindAudio()
      await this.loadPlaylist()
      await this.tryAutoplay()
    },

    bindAudio() {
      if (!audio) return
      audio.volume = this.volume
      audio.addEventListener('timeupdate', () => {
        this.currentTime = audio.currentTime
        this.duration = audio.duration || 0
        this.updateLyric()
      })
      audio.addEventListener('ended', () => this.next())
      audio.addEventListener('play', () => {
        errorStreak = 0
        this.playing = true
      })
      audio.addEventListener('pause', () => {
        this.playing = false
      })
      audio.addEventListener('error', () => {
        this.playing = false
        errorStreak++
        if (errorStreak >= this.songs.length) {
          errorStreak = 0
          return
        }
        this.next()
      })
    },

    async loadPlaylist() {
      this.loading = true
      try {
        const pid = await this.resolvePlaylistId()
        const res = await fetch(`https://api.i-meto.com/meting/api?server=netease&type=playlist&id=${pid}`)
        const data = await res.json()
        const arr = Array.isArray(data) ? data : []
        this.songs = arr
          .map((s) => ({
            title: s.title || s.name || '未知歌曲',
            artist: s.author || s.artist || '未知歌手',
            src: s.url || '',
            pic: s.pic || '',
            lrc: s.lrc || ''
          }))
          .filter((s) => s.src)
      } catch (e) {
        this.songs = await this.loadFallbackSongs()
      } finally {
        this.loading = false
      }
      // 歌单为空时也使用默认歌曲
      if (!this.songs.length) {
        this.songs = await this.loadFallbackSongs()
      }
    },

    /** 读取后台维护的默认歌曲，接口失败时使用内置兜底 */
    async loadFallbackSongs() {
      try {
        const list = await getFallbackSongs()
        const songs = (list || [])
          .map((s) => ({
            title: s.title || '未知歌曲',
            artist: s.artist || '',
            src: s.url || '',
            pic: s.pic || '',
            lrc: ''
          }))
          .filter((s) => s.src)
        if (songs.length) {
          return songs
        }
      } catch (e) {
        // 接口失败时继续使用内置兜底
      }
      return BUILTIN_FALLBACK
    },

    async resolvePlaylistId() {
      try {
        const id = await getActivePlaylist()
        return id || DEFAULT_PLAYLIST_ID
      } catch (e) {
        return DEFAULT_PLAYLIST_ID
      }
    },

    /** 打开页面自动播放，被浏览器拦截时在首次交互后补播 */
    async tryAutoplay() {
      if (!audio || !this.songs.length) return
      audio.src = this.songs[0].src
      this.loadLyrics(this.songs[0])
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
    },

    play(i) {
      if (!audio || !this.songs.length) return
      if (i === this.index && audio.src) {
        this.toggle()
        return
      }
      this.index = i
      audio.src = this.songs[i].src
      this.loadLyrics(this.songs[i])
      audio.play().catch(() => {})
    },

    toggle() {
      if (!audio) return
      if (!audio.src) {
        this.play(0)
        return
      }
      if (audio.paused) audio.play().catch(() => {})
      else audio.pause()
    },

    next() {
      if (!this.songs.length) return
      this.play((this.index + 1) % this.songs.length)
    },

    prev() {
      if (!this.songs.length) return
      this.play((this.index - 1 + this.songs.length) % this.songs.length)
    },

    /** 按 0 ~ 1 的比例跳转进度 */
    seekRatio(ratio) {
      if (!audio || !this.duration) return
      const r = Math.max(0, Math.min(1, ratio))
      audio.currentTime = r * this.duration
      this.currentTime = audio.currentTime
    },

    setVolume(v) {
      this.volume = v
      if (audio) audio.volume = v
    },

    /** 读取并解析当前歌曲歌词，失败时静默显示空歌词 */
    async loadLyrics(song) {
      this.lyrics = []
      this.currentLyric = ''
      const lrc = song && song.lrc ? String(song.lrc).trim() : ''
      if (!lrc) return
      try {
        let text = lrc
        // Meting 返回的通常是歌词地址，直接是 LRC 文本时以 [ 开头
        if (!lrc.startsWith('[')) {
          const res = await fetch(lrc)
          if (!res.ok) return
          text = await res.text()
        }
        this.lyrics = parseLrc(text)
        this.updateLyric()
      } catch (e) {
        this.lyrics = []
        this.currentLyric = ''
      }
    },

    updateLyric() {
      if (!this.lyrics.length) {
        this.currentLyric = ''
        return
      }
      let idx = -1
      for (let i = 0; i < this.lyrics.length; i++) {
        if (this.lyrics[i].time <= this.currentTime) idx = i
        else break
      }
      this.currentLyric = idx >= 0 ? this.lyrics[idx].text : ''
    }
  }
})
