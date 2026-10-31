<template>
  <div class="iris-loading navigation-loading">
    <div v-if="state.visible" class="route-progress" :class="{ done: state.done }" role="progressbar" aria-label="正在打开页面"><span></span></div>
    <Transition name="route-toast"><div v-if="state.visible && !state.done" class="route-toast" role="status"><span class="route-star" aria-hidden="true">✧</span><div><strong>IRIS 正在带路～</strong><small>下一段故事，马上就好</small></div><span class="route-dots" aria-hidden="true">···</span></div></Transition>
    <div v-if="state.error" class="route-toast route-error" role="alert"><span aria-hidden="true">✧</span><div><strong>页面暂时没能打开</strong><button @click="retry">重新加载</button><button @click="state.error = false">关闭</button></div></div>
  </div>
</template>
<script setup>
import { navigationLoading as state } from '@/utils/navigationLoading'
function retry() { window.location.assign(state.retryPath || window.location.href) }
</script>
<style scoped>
.navigation-loading { pointer-events:none; }.route-progress { position:fixed; z-index:2200; left:0; top:0; right:0; height:3px; overflow:hidden; background:#b193e51a; }.route-progress span { display:block; width:35%; height:100%; background:linear-gradient(90deg,#9b8de9,#e6acd1); box-shadow:0 0 10px #b293e9; animation:iris-progress 1.3s ease-in-out infinite; }.route-progress.done span { animation:none; width:100%; }
.route-toast { position:fixed; z-index:2100; right:24px; top:calc(var(--header-height,64px) + 18px); display:flex; align-items:center; gap:13px; padding:15px 19px; border:1px solid #b194dd55; border-radius:18px; background:var(--load-bg); color:var(--load-ink); box-shadow:0 12px 38px #24113c22; max-width:calc(100vw - 32px); }.route-toast strong { display:block; font-size:12px; font-weight:600; }.route-toast small { display:block; margin-top:5px; font-size:10px; color:var(--load-muted); }.route-star { font-size:30px; color:var(--load-glow); animation:iris-twinkle 1.5s ease-in-out infinite alternate; }.route-dots { letter-spacing:3px; color:var(--load-glow); }.route-error { pointer-events:auto; }.route-error button { color:var(--load-ink); background:#b194dd20; border:1px solid #b194dd55; border-radius:7px; padding:6px 10px; margin:9px 7px 0 0; cursor:pointer; }.route-error button:focus-visible { outline:2px solid var(--load-glow); outline-offset:2px; }
.route-toast-enter-active,.route-toast-leave-active { transition:opacity .18s,transform .18s; }.route-toast-enter-from,.route-toast-leave-to { opacity:0; transform:translateY(-8px); }
@media(max-width:600px) { .route-toast { right:16px; padding:12px 15px; } }
@media(prefers-reduced-motion:reduce) { .route-progress span,.route-star { animation:none; }.route-toast-enter-active,.route-toast-leave-active { transition:none; } }
</style>
