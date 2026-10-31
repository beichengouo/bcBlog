<template>
  <div class="city-scene" role="img" aria-label="失名星城断桥星渠场景，包含地面、墙体与前景" :style="{ aspectRatio: `${scene.width} / ${scene.height}` }">
    <img v-for="layer in scene.layers" :key="layer.image" :src="layer.image" alt="" loading="lazy" :style="layerStyle(layer)" />
  </div>
</template>
<script setup>
import codex from '@/config/gameCodex.json'
const scene = codex.cityScene
function layerStyle(layer) {
  const [x, y, width, height] = layer.rect
  return { left: `${x / scene.width * 100}%`, top: `${y / scene.height * 100}%`, width: `${width / scene.width * 100}%`, height: `${height / scene.height * 100}%`, zIndex: { background: 0, walls: 1, gates: 2, foreground: 3 }[layer.layer] || 0 }
}
</script>
<style scoped>
.city-scene { position: relative; width: 100%; overflow: hidden; background: #0c1725; isolation: isolate; }
.city-scene img { position: absolute; display: block; max-width: none; object-fit: fill; pointer-events: none; }
</style>
