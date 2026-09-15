/**
 * 沙盒地图标记布局工具。
 *
 * 多个标记（角色 / 地点）落在同一个坐标时会完全重叠、只能看到一个，
 * 这里把同坐标的标记以真实落点为圆心做扇形错开，保证每个都能看见。
 */

/** 距地图边缘保留的百分比边距 */
const MARGIN = 5

function clampPercent(value) {
  return Math.min(100 - MARGIN, Math.max(MARGIN, value))
}

/**
 * 计算标记的显示坐标。
 *
 * @param {Array} items 标记数组，元素需包含 x / y（0~100 百分比）
 * @param {number} radiusX 横向错开半径（百分比），默认 4.5
 * @returns {Array} 新数组，元素额外带 displayX / displayY / groupSize / anchorX / anchorY
 */
export function layoutMarkers(items, radiusX = 4.5) {
  const list = items || []
  const groups = new Map()
  list.forEach((item) => {
    const x = Number(item.x == null ? 50 : item.x)
    const y = Number(item.y == null ? 50 : item.y)
    const key = x + ',' + y
    if (!groups.has(key)) {
      groups.set(key, [])
    }
    groups.get(key).push({ item, x, y })
  })

  const result = []
  groups.forEach((members) => {
    const { x, y } = members[0]
    if (members.length === 1) {
      result.push({
        ...members[0].item,
        displayX: x,
        displayY: y,
        anchorX: x,
        anchorY: y,
        groupSize: 1
      })
      return
    }
    // 地图按 16:9 展示，纵向半径按比例缩小，错开后的分布看起来才是一个圆
    const radiusY = (radiusX * 9) / 16
    const total = members.length
    const offsets = members.map((member, index) => {
      const angle = -Math.PI / 2 + (Math.PI * 2 * index) / total
      return { item: member.item, dx: Math.cos(angle) * radiusX, dy: Math.sin(angle) * radiusY }
    })
    // 整组贴边时整体往地图内平移，保证错开后的间距不会被压扁
    const shiftX = calcShift(offsets.map((offset) => x + offset.dx))
    const shiftY = calcShift(offsets.map((offset) => y + offset.dy))
    offsets.forEach((offset) => {
      result.push({
        ...offset.item,
        displayX: clampPercent(x + offset.dx + shiftX),
        displayY: clampPercent(y + offset.dy + shiftY),
        anchorX: x,
        anchorY: y,
        groupSize: total
      })
    })
  })
  return result
}

/** 求一个偏移量，使一组坐标全部落在边距内；放不下时返回 0 */
function calcShift(values) {
  const lower = MARGIN - Math.min(...values)
  const upper = 100 - MARGIN - Math.max(...values)
  if (lower > upper) {
    return 0
  }
  return Math.min(Math.max(0, lower), upper)
}

/** 取出所有「多个标记挤在一起」的真实落点，用于在地图上画锚点 */
export function overlapAnchors(layoutItems) {
  const map = new Map()
  ;(layoutItems || []).forEach((item) => {
    if (item.groupSize > 1) {
      map.set(item.anchorX + ',' + item.anchorY, {
        x: item.anchorX,
        y: item.anchorY,
        size: item.groupSize
      })
    }
  })
  return Array.from(map.values())
}
