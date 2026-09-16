/**
 * 沙盒地图「魔法棒」描边工具（纯前端，不需要任何模型或服务）。
 *
 * 思路：点地图上某一块 → 按颜色做泛洪扩散找出整块连通区域 →
 *       沿像素边界（裂缝）提取轮廓 → Douglas-Peucker 抽稀成一圈多边形顶点 →
 *       换算成 0~100 的百分比坐标。
 *
 * 适合手绘/卡通风格的地图（每块区域颜色比较均匀）；渐变丰富或纹理细腻的地图会溢色，
 * 这种情况就退回手工套索，或者生成草稿后再手动拖顶点调整。
 */

import { simplifyPath } from './sandboxGeo.js'

/**
 * 颜色泛洪：从 (startX, startY) 开始扩散，返回二值掩码。
 * @returns {{mask: Uint8Array, count: number, overflow: boolean}}
 */
export function floodFillMask(data, width, height, startX, startY, tolerance = 32, maxPixels = 400000) {
  const mask = new Uint8Array(width * height)
  const startIndex = startY * width + startX
  const base = startIndex * 4
  const sr = data[base]
  const sg = data[base + 1]
  const sb = data[base + 2]
  const sa = data[base + 3]
  const limit = tolerance * tolerance * 3
  const stack = [startIndex]
  mask[startIndex] = 1
  let count = 1
  while (stack.length) {
    const point = stack.pop()
    const x = point % width
    const y = (point - x) / width
    for (let k = 0; k < 4; k++) {
      const nx = x + (k === 0 ? -1 : k === 1 ? 1 : 0)
      const ny = y + (k === 2 ? -1 : k === 3 ? 1 : 0)
      if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
      const np = ny * width + nx
      if (mask[np]) continue
      const o = np * 4
      const dr = data[o] - sr
      const dg = data[o + 1] - sg
      const db = data[o + 2] - sb
      const da = data[o + 3] - sa
      if (dr * dr + dg * dg + db * db + da * da > limit) continue
      mask[np] = 1
      count++
      if (count > maxPixels) {
        return { mask, count, overflow: true }
      }
      stack.push(np)
    }
  }
  return { mask, count, overflow: false }
}

/**
 * 提取掩码的边界环路。
 *
 * 做法是把每个前景像素的 4 条「外露面」当成一条有向边（沿边界逆时针走），
 * 再把这些边首尾相接串成闭环。比轮廓跟踪算法更直观，也不会因为
 * 一点细节就走丢；区域内部有洞（比如森林里的湖）时会得到多个环，
 * 调用方取面积最大的那个（外轮廓）。
 */
export function boundaryLoops(mask, width, height) {
  const segments = new Map()
  const addSegment = (x1, y1, x2, y2) => {
    const key = `${x1},${y1}`
    const list = segments.get(key)
    if (list) {
      list.push([x2, y2])
    } else {
      segments.set(key, [[x2, y2]])
    }
  }
  const fg = (x, y) => x >= 0 && y >= 0 && x < width && y < height && mask[y * width + x] === 1

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (!fg(x, y)) continue
      if (!fg(x, y - 1)) addSegment(x + 1, y, x, y)
      if (!fg(x - 1, y)) addSegment(x, y, x, y + 1)
      if (!fg(x, y + 1)) addSegment(x, y + 1, x + 1, y + 1)
      if (!fg(x + 1, y)) addSegment(x + 1, y + 1, x + 1, y)
    }
  }

  const used = new Set()
  const loops = []
  for (const [key, ends] of segments) {
    for (const end of ends) {
      if (used.has(`${key}>${end[0]},${end[1]}`)) continue
      const loop = []
      let [cx, cy] = key.split(',').map(Number)
      const startKey = key
      let guard = 0
      while (guard++ < 2000000) {
        loop.push([cx, cy])
        const currentKey = `${cx},${cy}`
        const nexts = segments.get(currentKey)
        if (!nexts || nexts.length === 0) break
        let chosen = null
        for (const next of nexts) {
          const segKey = `${currentKey}>${next[0]},${next[1]}`
          if (!used.has(segKey)) {
            used.add(segKey)
            chosen = next
            break
          }
        }
        if (!chosen) break
        cx = chosen[0]
        cy = chosen[1]
        if (`${cx},${cy}` === startKey) break
      }
      if (loop.length > 3) loops.push(loop)
    }
  }
  return loops
}

function shoelace(points) {
  let sum = 0
  for (let i = 0; i < points.length; i++) {
    const cur = points[i]
    const next = points[(i + 1) % points.length]
    sum += cur[0] * next[1] - next[0] * cur[1]
  }
  return sum / 2
}

/** 掩码转多边形（0~100 百分比坐标），取面积最大的那条外轮廓并抽稀 */
export function maskToPolygon(mask, width, height, tolerance = 0.8) {
  const loops = boundaryLoops(mask, width, height)
  if (!loops.length) return []
  let main = loops[0]
  for (const loop of loops) {
    if (Math.abs(shoelace(loop)) > Math.abs(shoelace(main))) main = loop
  }
  // RDP 需要一条首尾相接的开放折线：把起点补到末尾再简化
  const closed = main.concat([[main[0][0], main[0][1]]])
  const simplified = simplifyPath(closed, tolerance)
  if (simplified.length > 1 && simplified[0][0] === simplified[simplified.length - 1][0]
      && simplified[0][1] === simplified[simplified.length - 1][1]) {
    simplified.pop()
  }
  // RDP 把闭环当成开放折线处理，起点往往落在某条边的中间，
  // 于是「最后一段 + 第一段」本该是同一条边。这里再合并一次。
  const cleaned = mergeWrapCollinear(simplified, tolerance)
  return cleaned.map(([x, y]) => [
    Math.round(((x / width) * 100) * 10) / 10,
    Math.round(((y / height) * 100) * 10) / 10
  ])
}

/** 合并闭环首尾处的共线冗余顶点 */
function mergeWrapCollinear(points, tolerance) {
  const result = points.slice()
  while (result.length > 3) {
    const last = result[result.length - 1]
    const first = result[0]
    const second = result[1]
    if (distanceToSegment(first[0], first[1], last[0], last[1], second[0], second[1]) <= tolerance) {
      result.shift()
    } else {
      break
    }
  }
  return result
}

function distanceToSegment(px, py, x1, y1, x2, y2) {
  const dx = x2 - x1
  const dy = y2 - y1
  const lengthSquared = dx * dx + dy * dy
  if (lengthSquared <= 1e-12) return Math.hypot(px - x1, py - y1)
  let t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared
  t = Math.max(0, Math.min(1, t))
  return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy))
}

/**
 * 魔法棒入口：传入地图图片元素和点击位置（百分比），返回多边形草稿。
 * @returns {{ok: boolean, polygon?: number[][], message?: string, pixels?: number}}
 */
export function magicWandPolygon(image, percentX, percentY, options = {}) {
  const tolerance = options.tolerance == null ? 32 : options.tolerance
  const maxSize = options.maxSize == null ? 600 : options.maxSize
  const maxPixels = options.maxPixels == null ? 200000 : options.maxPixels
  const simplifyTolerance = options.simplifyTolerance == null ? 0.8 : options.simplifyTolerance
  if (!image) {
    return { ok: false, message: '地图背景还没加载好，请稍后再试' }
  }
  const naturalWidth = image.naturalWidth || image.width
  const naturalHeight = image.naturalHeight || image.height
  if (!naturalWidth || !naturalHeight) {
    return { ok: false, message: '地图背景尺寸读取失败' }
  }
  // 缩放到 maxSize 以内再处理：速度快很多，精度对描边来说完全够用
  const scale = Math.min(1, maxSize / Math.max(naturalWidth, naturalHeight))
  const width = Math.max(1, Math.round(naturalWidth * scale))
  const height = Math.max(1, Math.round(naturalHeight * scale))
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  ctx.drawImage(image, 0, 0, width, height)
  let data
  try {
    data = ctx.getImageData(0, 0, width, height).data
  } catch (e) {
    return { ok: false, message: '图片跨域限制导致无法读取像素，请把地图换成站内上传的图片' }
  }
  const startX = Math.max(0, Math.min(width - 1, Math.round((percentX / 100) * width)))
  const startY = Math.max(0, Math.min(height - 1, Math.round((percentY / 100) * height)))
  const filled = floodFillMask(data, width, height, startX, startY, tolerance, maxPixels)
  if (filled.overflow) {
    return { ok: false, message: '这块颜色区域太大了（可能是背景或渐变色），换个颜色更纯粹的小区域点点看' }
  }
  const polygon = maskToPolygon(filled.mask, width, height, simplifyTolerance)
  if (polygon.length < 3) {
    return { ok: false, message: '没描出有效轮廓，换个位置点点看' }
  }
  return { ok: true, polygon, pixels: filled.count }
}
