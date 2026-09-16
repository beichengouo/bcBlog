/**
 * 沙盒地图几何工具（前端）。
 *
 * ⚠️ 这份算法必须与后端 `com.bc.bcblog.common.SandboxGeo` 保持一致：
 *    后端用这套规则判定「角色属于哪个区域」，前台用同一套规则把角色显示到对应区域里。
 *    两边一旦不一致，就会出现「角色名字说在 A、地图上却显示在 B」这种灵异现象。
 *
 * 坐标统一是地图百分比：x 向右、y 向下，取值 0~100。
 */

/** 地图边长（百分比），整张地图面积 = 100 × 100 = 10000 单位² */
export const MAP_SIZE = 100
/**
 * 区域重叠容差：手绘压边的误差，10 单位² 以内算「贴边」放行
 * （相当于一条 10 单位长、1 单位宽的窄缝）。
 */
export const OVERLAP_TOLERANCE_AREA = 10
/** 区域重叠容差（相对量）：不超过较小区域面积的 3%，大区域按比例放宽 */
export const OVERLAP_TOLERANCE_RATIO = 0.03
/** 边界命中容差：点正好落在边上时也算命中 */
export const EDGE_TOLERANCE = 0.5
/** 网格采样精度：把地图切成 N×N 格估算重叠面积与覆盖率 */
export const GRID = 100
/** 区域之间「重叠高亮」用的粗网格，跑得快一些 */
export const COARSE_GRID = 50

/** 两块区域的关系 */
export const REL_DISJOINT = 0
export const REL_CROSS = 1
export const REL_A_INSIDE_B = 2
export const REL_B_INSIDE_A = 3

function clampCoord(value) {
  return Math.max(0, Math.min(MAP_SIZE, value))
}

/** 解析多边形 JSON；格式非法或顶点不足时返回空数组 */
export function parsePolygon(json) {
  if (!json) return []
  let raw = json
  if (typeof json === 'string') {
    try {
      raw = JSON.parse(json)
    } catch (e) {
      return []
    }
  }
  if (!Array.isArray(raw)) return []
  const points = []
  for (const item of raw) {
    if (!Array.isArray(item) || item.length < 2) continue
    const x = Number(item[0])
    const y = Number(item[1])
    if (!Number.isFinite(x) || !Number.isFinite(y)) continue
    points.push([clampCoord(x), clampCoord(y)])
  }
  return points
}

/** 多边形转 JSON（整数不带小数点，其它保留一位小数） */
export function polygonToJson(polygon) {
  const body = (polygon || [])
    .map(([x, y]) => `[${roundNum(x)},${roundNum(y)}]`)
    .join(',')
  return `[${body}]`
}

function roundNum(value) {
  const rounded = Math.round(value * 10) / 10
  return Number.isInteger(rounded) ? String(rounded) : String(rounded)
}

/** 矩形区域转 4 个顶点的多边形（老数据兼容 / 编辑时的初始形状） */
export function rectPolygon(x, y, width, height) {
  return [
    [x, y],
    [x + width, y],
    [x + width, y + height],
    [x, y + height]
  ]
}

/**
 * 取地点的判定多边形：
 *   1. 有 polygon（套索画的）→ 用多边形；
 *   2. 没有 polygon 但有宽高 → 矩形的 4 个角（老数据等价迁移）；
 *   3. 宽高都是 0（单点地点）→ 返回 null，由调用方按「点 + 容差」处理。
 */
export function polygonOf(location) {
  if (!location) return null
  const polygon = parsePolygon(location.polygon)
  if (polygon.length >= 3) return polygon
  const width = Number(location.width || 0)
  const height = Number(location.height || 0)
  if (width > 0 && height > 0) {
    return rectPolygon(Number(location.x || 0), Number(location.y || 0), width, height)
  }
  return null
}

/** 规范化：去掉重复顶点与多余共线顶点（套索点常常挤在一起） */
export function normalizePolygon(polygon) {
  const result = []
  for (const point of polygon || []) {
    const last = result[result.length - 1]
    if (last && Math.abs(last[0] - point[0]) < 1e-6 && Math.abs(last[1] - point[1]) < 1e-6) continue
    result.push([point[0], point[1]])
  }
  while (result.length > 1) {
    const first = result[0]
    const last = result[result.length - 1]
    if (Math.abs(first[0] - last[0]) < 1e-6 && Math.abs(first[1] - last[1]) < 1e-6) {
      result.pop()
    } else {
      break
    }
  }
  let changed = true
  while (changed && result.length > 3) {
    changed = false
    for (let i = 0; i < result.length; i++) {
      const prev = result[(i - 1 + result.length) % result.length]
      const cur = result[i]
      const next = result[(i + 1) % result.length]
      if (Math.abs(cross(prev, cur, next)) < 0.5) {
        result.splice(i, 1)
        changed = true
        break
      }
    }
  }
  return result
}

/** 多边形面积（鞋带公式） */
export function polygonArea(polygon) {
  if (!polygon || polygon.length < 3) return 0
  let sum = 0
  for (let i = 0; i < polygon.length; i++) {
    const cur = polygon[i]
    const next = polygon[(i + 1) % polygon.length]
    sum += cur[0] * next[1] - next[0] * cur[1]
  }
  return Math.abs(sum) / 2
}

/** 面积加权形心；退化时退回顶点平均 */
export function polygonCentroid(polygon) {
  if (!polygon || polygon.length === 0) return [50, 50]
  if (polygon.length < 3) return average(polygon)
  let signedArea = 0
  let cx = 0
  let cy = 0
  for (let i = 0; i < polygon.length; i++) {
    const cur = polygon[i]
    const next = polygon[(i + 1) % polygon.length]
    const factor = cur[0] * next[1] - next[0] * cur[1]
    signedArea += factor
    cx += (cur[0] + next[0]) * factor
    cy += (cur[1] + next[1]) * factor
  }
  if (Math.abs(signedArea) < 1e-9) return average(polygon)
  return [cx / (3 * signedArea), cy / (3 * signedArea)]
}

/** 取一个一定在多边形内部的点（逐行扫描找最长内部水平线中点） */
export function interiorPoint(polygon) {
  if (!polygon || polygon.length < 3) return polygonCentroid(polygon)
  const box = bbox(polygon)
  let bestWidth = -1
  let best = null
  const steps = 60
  for (let i = 1; i < steps; i++) {
    const y = box[1] + ((box[3] - box[1]) * i) / steps
    const xs = []
    for (let k = 0; k < polygon.length; k++) {
      const p1 = polygon[k]
      const p2 = polygon[(k + 1) % polygon.length]
      if (p1[1] > y !== p2[1] > y) {
        xs.push(p1[0] + ((y - p1[1]) / (p2[1] - p1[1])) * (p2[0] - p1[0]))
      }
    }
    if (xs.length < 2) continue
    xs.sort((a, b) => a - b)
    for (let k = 0; k + 1 < xs.length; k += 2) {
      const width = xs[k + 1] - xs[k]
      if (width <= bestWidth) continue
      const midX = (xs[k] + xs[k + 1]) / 2
      if (containsPoint(polygon, midX, y)) {
        bestWidth = width
        best = [midX, y]
      }
    }
  }
  return best || polygonCentroid(polygon)
}

/** 区域标注点：形心落在区域外（凹多边形）时退回内部点 */
export function labelPoint(polygon) {
  const center = polygonCentroid(polygon)
  if (containsPoint(polygon, center[0], center[1])) return center
  return interiorPoint(polygon)
}

/** 点是否在多边形内（含边界容差） */
export function containsPoint(polygon, x, y) {
  if (!polygon || polygon.length < 3) return false
  if (onEdge(polygon, x, y, EDGE_TOLERANCE)) return true
  return containsStrict(polygon, x, y)
}

/** 严格判定（不做边界容差） */
export function containsStrict(polygon, x, y) {
  if (!polygon || polygon.length < 3) return false
  let inside = false
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const xi = polygon[i][0]
    const yi = polygon[i][1]
    const xj = polygon[j][0]
    const yj = polygon[j][1]
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) {
      inside = !inside
    }
  }
  return inside
}

/** 点是否落在多边形边界上（容差 tol） */
export function onEdge(polygon, x, y, tol = EDGE_TOLERANCE) {
  if (!polygon || polygon.length < 2) return false
  for (let i = 0; i < polygon.length; i++) {
    const p1 = polygon[i]
    const p2 = polygon[(i + 1) % polygon.length]
    if (pointToSegment(x, y, p1[0], p1[1], p2[0], p2[1]) <= tol) return true
  }
  return false
}

/** 点到多边形距离：在内部为 0 */
export function distanceToPolygon(polygon, x, y) {
  if (!polygon || polygon.length === 0) return Number.MAX_VALUE
  if (containsPoint(polygon, x, y)) return 0
  if (polygon.length === 1) return Math.hypot(polygon[0][0] - x, polygon[0][1] - y)
  let best = Number.MAX_VALUE
  for (let i = 0; i < polygon.length; i++) {
    const p1 = polygon[i]
    const p2 = polygon[(i + 1) % polygon.length]
    best = Math.min(best, pointToSegment(x, y, p1[0], p1[1], p2[0], p2[1]))
  }
  return best
}

/** 把区域外的点投影到最近的边界上 */
export function projectToPolygon(polygon, x, y) {
  let best = null
  let bestDistance = Number.MAX_VALUE
  if (polygon && polygon.length >= 2) {
    for (let i = 0; i < polygon.length; i++) {
      const p1 = polygon[i]
      const p2 = polygon[(i + 1) % polygon.length]
      const candidate = closestPointOnSegment(x, y, p1[0], p1[1], p2[0], p2[1])
      const distance = Math.hypot(candidate[0] - x, candidate[1] - y)
      if (distance < bestDistance) {
        bestDistance = distance
        best = candidate
      }
    }
  }
  return best || polygonCentroid(polygon)
}

/** 外接矩形 [minX, minY, maxX, maxY] */
export function bbox(polygon) {
  if (!polygon || polygon.length === 0) return [50, 50, 50, 50]
  let minX = Number.MAX_VALUE
  let minY = Number.MAX_VALUE
  let maxX = -Number.MAX_VALUE
  let maxY = -Number.MAX_VALUE
  for (const point of polygon) {
    minX = Math.min(minX, point[0])
    minY = Math.min(minY, point[1])
    maxX = Math.max(maxX, point[0])
    maxY = Math.max(maxY, point[1])
  }
  return [minX, minY, maxX, maxY]
}

/** 多边形是否自交（数字 8 的画法） */
export function selfIntersects(polygon) {
  if (!polygon || polygon.length < 4) return false
  const size = polygon.length
  for (let i = 0; i < size; i++) {
    const a1 = polygon[i]
    const a2 = polygon[(i + 1) % size]
    for (let j = i + 1; j < size; j++) {
      if (j === i || (j + 1) % size === i || (i + 1) % size === j) continue
      const b1 = polygon[j]
      const b2 = polygon[(j + 1) % size]
      if (segmentsIntersect(a1, a2, b1, b2)) return true
    }
  }
  return false
}

/** 两块区域的重叠面积（单位²），网格采样估算 */
export function overlapArea(a, b, grid = GRID) {
  if (!a || !b || a.length < 3 || b.length < 3) return 0
  const boxA = bbox(a)
  const boxB = bbox(b)
  if (boxA[2] < boxB[0] || boxB[2] < boxA[0] || boxA[3] < boxB[1] || boxB[3] < boxA[1]) return 0
  const step = MAP_SIZE / grid
  let count = 0
  for (let i = 0; i < grid; i++) {
    const x = (i + 0.5) * step
    for (let j = 0; j < grid; j++) {
      const y = (j + 0.5) * step
      // 这里用严格判定：否则紧贴的两块区域会因为边界容差被误判成重叠
      if (containsStrict(a, x, y) && containsStrict(b, x, y)) count++
    }
  }
  return count * step * step
}

/** 重叠面积占整张地图的百分比 */
export function overlapPercent(a, b, grid = GRID) {
  return (overlapArea(a, b, grid) / (MAP_SIZE * MAP_SIZE)) * 100
}

/** 重叠面积占「较小那块区域」的百分比（提示语里用这个更直观） */
export function overlapRatio(a, b, grid = GRID) {
  const smaller = Math.min(polygonArea(a), polygonArea(b))
  if (smaller <= 0) return 0
  return Math.min(100, (overlapArea(a, b, grid) / smaller) * 100)
}

/**
 * 这点重叠算不算「贴边误差」：容差取「10 单位²」与「较小区域面积 3%」里更大的那个。
 * 小区域靠绝对量兜底（手绘精度有限），大区域靠比例放宽（长边压边是正常的）。
 */
export function overlapAllowed(a, b, grid = GRID) {
  const smaller = Math.min(polygonArea(a), polygonArea(b))
  const limit = Math.max(OVERLAP_TOLERANCE_AREA, smaller * OVERLAP_TOLERANCE_RATIO)
  return overlapArea(a, b, grid) <= limit
}

/** 两块区域的关系：相离 / 交叉重叠 / 一方完全包含另一方 */
export function relation(a, b) {
  if (!a || !b || a.length < 3 || b.length < 3) return REL_DISJOINT
  for (let i = 0; i < a.length; i++) {
    const a1 = a[i]
    const a2 = a[(i + 1) % a.length]
    for (let j = 0; j < b.length; j++) {
      const b1 = b[j]
      const b2 = b[(j + 1) % b.length]
      if (segmentsIntersect(a1, a2, b1, b2)) return REL_CROSS
    }
  }
  if (allInside(a, b)) return REL_A_INSIDE_B
  if (allInside(b, a)) return REL_B_INSIDE_A
  return REL_DISJOINT
}

/** 取出两块区域重叠的格子坐标（用于把冲突部分标红） */
export function overlapCells(a, b, grid = COARSE_GRID) {
  const cells = []
  if (!a || !b || a.length < 3 || b.length < 3) return cells
  const step = MAP_SIZE / grid
  for (let i = 0; i < grid; i++) {
    const x = (i + 0.5) * step
    for (let j = 0; j < grid; j++) {
      const y = (j + 0.5) * step
      if (containsStrict(a, x, y) && containsStrict(b, x, y)) {
        cells.push([i * step, j * step, step, step])
        if (cells.length > 600) return cells
      }
    }
  }
  return cells
}

/** 区域覆盖率：所有地点合起来覆盖了地图多少面积（百分比） */
export function coveragePercent(locations, grid = COARSE_GRID) {
  const polygons = (locations || []).map((loc) => polygonOf(loc)).filter(Boolean)
  if (polygons.length === 0) return 0
  const step = MAP_SIZE / grid
  let covered = 0
  for (let i = 0; i < grid; i++) {
    const x = (i + 0.5) * step
    for (let j = 0; j < grid; j++) {
      const y = (j + 0.5) * step
      if (polygons.some((polygon) => containsStrict(polygon, x, y))) covered++
    }
  }
  return (covered / (grid * grid)) * 100
}

/** 坐标是否落在地点区域内（单点地点允许 2% 容差） */
export function inLocationArea(location, x, y) {
  const polygon = polygonOf(location)
  if (polygon) return containsPoint(polygon, x, y)
  return Math.abs(Number(location.x || 50) - x) <= 2 && Math.abs(Number(location.y || 50) - y) <= 2
}

/** 点到地点区域的距离：在区域内为 0 */
export function distanceToLocation(location, x, y) {
  const polygon = polygonOf(location)
  if (polygon) return distanceToPolygon(polygon, x, y)
  return Math.hypot(Number(location.x || 50) - x, Number(location.y || 50) - y)
}

/**
 * 坐标落在哪个地点区域内（多条命中取面积最小的，区域允许嵌套）。
 * 没命中时按「最近的区域」兜底，避免角色掉进区域之间的缝隙后无处可归。
 */
export function locationAtPoint(locations, x, y, withFallback = true) {
  let best = null
  let bestArea = Number.MAX_VALUE
  for (const location of locations || []) {
    if (!inLocationArea(location, x, y)) continue
    const area = polygonArea(polygonOf(location))
    if (area < bestArea) {
      bestArea = area
      best = location
    }
  }
  if (best || !withFallback) return best
  let nearest = null
  let nearestDistance = Number.MAX_VALUE
  for (const location of locations || []) {
    const distance = distanceToLocation(location, x, y)
    if (distance < nearestDistance) {
      nearestDistance = distance
      nearest = location
    }
  }
  return nearest
}

/** 角色归属的地点：优先按名字匹配（后端写入的 location_name），再按坐标判定 */
export function locationOfCharacter(character, locations) {
  if (!character) return null
  const byName = (locations || []).find((loc) => loc.name === character.locationName)
  if (byName) return byName
  return locationAtPoint(locations, Number(character.x == null ? 50 : character.x), Number(character.y == null ? 50 : character.y))
}

/**
 * 顶点吸附：把一个点吸到附近已有的顶点或区域边界上。
 * 有了它，相邻区域才能做到「既贴边又不重叠」。
 */
export function snapPoint(point, locations, options = {}) {
  const vertexTolerance = options.vertexTolerance == null ? 2 : options.vertexTolerance
  const edgeTolerance = options.edgeTolerance == null ? 1.5 : options.edgeTolerance
  const excludeId = options.excludeId
  const ignore = options.ignore
  let best = { point: [point[0], point[1]], type: 'none', distance: Number.MAX_VALUE }
  for (const location of locations || []) {
    if (excludeId != null && location.id === excludeId) continue
    if (ignore && ignore(location)) continue
    const polygon = polygonOf(location)
    if (!polygon) continue
    for (const vertex of polygon) {
      const distance = Math.hypot(vertex[0] - point[0], vertex[1] - point[1])
      if (distance <= vertexTolerance && distance < best.distance) {
        best = { point: [vertex[0], vertex[1]], type: 'vertex', distance, name: location.name }
      }
    }
    for (let i = 0; i < polygon.length; i++) {
      const p1 = polygon[i]
      const p2 = polygon[(i + 1) % polygon.length]
      const candidate = closestPointOnSegment(point[0], point[1], p1[0], p1[1], p2[0], p2[1])
      const distance = Math.hypot(candidate[0] - point[0], candidate[1] - point[1])
      if (distance <= edgeTolerance && distance < best.distance) {
        best = { point: candidate, type: 'edge', distance, name: location.name }
      }
    }
  }
  return best
}

/** 向量化简（Douglas-Peucker），魔法棒描出来的轮廓点太多时用来抽稀 */
export function simplifyPath(points, tolerance = 0.4) {
  if (!points || points.length < 3) return (points || []).slice()
  const keep = new Array(points.length).fill(false)
  keep[0] = true
  keep[points.length - 1] = true
  const stack = [[0, points.length - 1]]
  while (stack.length) {
    const [start, end] = stack.pop()
    let maxDistance = 0
    let index = -1
    for (let i = start + 1; i < end; i++) {
      const distance = pointToSegment(points[i][0], points[i][1], points[start][0], points[start][1], points[end][0], points[end][1])
      if (distance > maxDistance) {
        maxDistance = distance
        index = i
      }
    }
    if (maxDistance > tolerance && index > 0) {
      keep[index] = true
      stack.push([start, index], [index, end])
    }
  }
  return points.filter((point, index) => keep[index])
}

// ============================== 内部数学工具 ==============================

function average(polygon) {
  if (!polygon || polygon.length === 0) return [50, 50]
  let sx = 0
  let sy = 0
  for (const point of polygon) {
    sx += point[0]
    sy += point[1]
  }
  return [sx / polygon.length, sy / polygon.length]
}

function cross(a, b, c) {
  return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
}

function pointToSegment(px, py, x1, y1, x2, y2) {
  const closest = closestPointOnSegment(px, py, x1, y1, x2, y2)
  return Math.hypot(closest[0] - px, closest[1] - py)
}

function closestPointOnSegment(px, py, x1, y1, x2, y2) {
  const dx = x2 - x1
  const dy = y2 - y1
  const lengthSquared = dx * dx + dy * dy
  if (lengthSquared <= 1e-12) return [x1, y1]
  let t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared
  t = Math.max(0, Math.min(1, t))
  return [x1 + t * dx, y1 + t * dy]
}

function allInside(a, b) {
  return a.every((point) => containsPoint(b, point[0], point[1]))
}

function segmentsIntersect(a1, a2, b1, b2) {
  const d1 = cross(b1, b2, a1)
  const d2 = cross(b1, b2, a2)
  const d3 = cross(a1, a2, b1)
  const d4 = cross(a1, a2, b2)
  if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
  if (Math.abs(d1) < 1e-9 && onSegment(b1, b2, a1)) return true
  if (Math.abs(d2) < 1e-9 && onSegment(b1, b2, a2)) return true
  if (Math.abs(d3) < 1e-9 && onSegment(a1, a2, b1)) return true
  return Math.abs(d4) < 1e-9 && onSegment(a1, a2, b2)
}

function onSegment(p1, p2, p) {
  return (
    p[0] >= Math.min(p1[0], p2[0]) - 1e-9 &&
    p[0] <= Math.max(p1[0], p2[0]) + 1e-9 &&
    p[1] >= Math.min(p1[1], p2[1]) - 1e-9 &&
    p[1] <= Math.max(p1[1], p2[1]) + 1e-9
  )
}
