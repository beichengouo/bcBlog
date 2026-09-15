/**
 * 沙盒地点内置图标库。
 *
 * 约定：sandbox_location.icon 存两种值
 *   1. 内置图标 key（例如 forest、tower），前端渲染成 SVG；
 *   2. 上传后的图片地址（以 /uploads/ 或 http 开头），前端直接 <img> 展示。
 */
export const sandboxIcons = [
  {
    key: 'pin',
    name: '默认图钉',
    paths: [
      'M12 21s7-6.1 7-11a7 7 0 1 0-14 0c0 4.9 7 11 7 11z',
      'M14 10a2 2 0 1 1-4 0 2 2 0 0 1 4 0'
    ]
  },
  {
    key: 'village',
    name: '村庄',
    paths: ['M3 10.5 12 3l9 7.5V21a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z']
  },
  {
    key: 'forest',
    name: '森林',
    paths: ['M12 3 6.5 11H10l-4 6h12l-4-6h3.5z', 'M12 17v4']
  },
  {
    key: 'tower',
    name: '魔法塔',
    paths: ['M9 21V8l3-3 3 3v13', 'M6 21h12', 'M10 12h4']
  },
  {
    key: 'lake',
    name: '湖泊',
    paths: ['M3 8c1.5 1.2 3 1.2 4.5 0S10.5 6.8 12 8s3 1.2 4.5 0S19.5 6.8 21 8',
      'M3 13c1.5 1.2 3 1.2 4.5 0S10.5 11.8 12 13s3 1.2 4.5 0S19.5 11.8 21 13',
      'M3 18c1.5 1.2 3 1.2 4.5 0S10.5 16.8 12 18s3 1.2 4.5 0S19.5 16.8 21 18']
  },
  {
    key: 'ruins',
    name: '遗迹',
    paths: ['M12 2 3 7h18z', 'M3 22h18', 'M6 18v-8', 'M10 18v-8', 'M14 18v-8', 'M18 18v-8']
  },
  {
    key: 'tavern',
    name: '酒馆',
    paths: ['M8 3h8l-1 7a3 3 0 0 1-6 0z', 'M12 10v8', 'M8 21h8']
  },
  {
    key: 'market',
    name: '集市',
    paths: ['M6 2 4 6v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6l-2-4z', 'M4 6h16', 'M15 10a3 3 0 0 1-6 0']
  },
  {
    key: 'mountain',
    name: '山脉',
    paths: ['M8 3 12 11l5-5 5 15H2z']
  },
  {
    key: 'flag',
    name: '据点',
    paths: ['M4 22V4', 'M4 4h12l-2 4 2 4H4']
  },
  {
    key: 'portal',
    name: '传送门',
    paths: ['M12 3v3', 'M12 18v3', 'M3 12h3', 'M18 12h3', 'M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10']
  }
]

/** 按 key 找内置图标，找不到返回默认图钉 */
export function findBuiltinIcon(key) {
  return sandboxIcons.find((item) => item.key === key) || sandboxIcons[0]
}

/**
 * 判断 icon 字段是内置图标还是自定义图片。
 * 返回 { type: 'image', url } 或 { type: 'builtin', icon }
 */
export function resolveSandboxIcon(icon) {
  if (icon && (/^https?:\/\//i.test(icon) || icon.startsWith('/'))) {
    return { type: 'image', url: icon }
  }
  return { type: 'builtin', icon: findBuiltinIcon(icon) }
}
