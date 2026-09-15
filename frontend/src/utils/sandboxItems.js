/**
 * 沙盒背包物品的展示辅助：品质配色 + 按物品名匹配图标。
 * 管理员上传了自定义图标时用图片，否则用这里匹配到的 emoji，保证任何物品都有图标。
 */

/** 物品品质：1 普通 / 2 精良 / 3 稀有 / 4 史诗 / 5 传说 */
export const ITEM_RARITIES = [
  { value: 1, name: '普通', color: '#b9b3c9', bg: 'rgba(185, 179, 201, 0.16)', border: 'rgba(185, 179, 201, 0.45)' },
  { value: 2, name: '精良', color: '#63c07a', bg: 'rgba(99, 192, 122, 0.16)', border: 'rgba(99, 192, 122, 0.5)' },
  { value: 3, name: '稀有', color: '#5b9bd5', bg: 'rgba(91, 155, 213, 0.18)', border: 'rgba(91, 155, 213, 0.55)' },
  { value: 4, name: '史诗', color: '#a875e0', bg: 'rgba(168, 117, 224, 0.2)', border: 'rgba(168, 117, 224, 0.6)' },
  { value: 5, name: '传说', color: '#f0b13c', bg: 'rgba(240, 177, 60, 0.22)', border: 'rgba(240, 177, 60, 0.65)' }
]

export function rarityMeta(rarity) {
  const value = Number(rarity) || 1
  return ITEM_RARITIES.find((item) => item.value === value) || ITEM_RARITIES[0]
}

const ITEM_EMOJIS = [
  { keys: ['面包', '干粮', '饼', '食物'], emoji: '🍞' },
  { keys: ['肉'], emoji: '🍖' },
  { keys: ['鱼'], emoji: '🐟' },
  { keys: ['苹果', '果'], emoji: '🍎' },
  { keys: ['蛋'], emoji: '🥚' },
  { keys: ['药水', '药剂'], emoji: '🧪' },
  { keys: ['草药', '药草', '草'], emoji: '🌿' },
  { keys: ['花'], emoji: '🌸' },
  { keys: ['种子'], emoji: '🌱' },
  { keys: ['蘑菇'], emoji: '🍄' },
  { keys: ['酒'], emoji: '🍶' },
  { keys: ['茶'], emoji: '🍵' },
  { keys: ['水', '壶', '瓶'], emoji: '🧴' },
  { keys: ['戒指'], emoji: '💍' },
  { keys: ['项链', '护符', '饰品'], emoji: '📿' },
  { keys: ['宝石', '水晶', '晶', '碎片', '星辉'], emoji: '💎' },
  { keys: ['矿石', '石头', '岩石', '铁', '煤'], emoji: '🪨' },
  { keys: ['剑'], emoji: '🗡️' },
  { keys: ['弓', '箭'], emoji: '🏹' },
  { keys: ['盾'], emoji: '🛡️' },
  { keys: ['杖'], emoji: '🪄' },
  { keys: ['地图'], emoji: '🗺️' },
  { keys: ['书', '笔记', '卷轴', '手册'], emoji: '📖' },
  { keys: ['钥匙', '秘钥'], emoji: '🗝️' },
  { keys: ['灯', '灯笼', '火把'], emoji: '🏮' },
  { keys: ['羽毛'], emoji: '🪶' },
  { keys: ['绳'], emoji: '🪢' },
  { keys: ['斗篷', '披风', '布', '衣'], emoji: '🧥' },
  { keys: ['鞋', '靴'], emoji: '👟' },
  { keys: ['帽'], emoji: '🎩' },
  { keys: ['金币', '钱袋', '硬币'], emoji: '🪙' },
  { keys: ['木材', '木头', '木柴', '树枝', '木'], emoji: '🪵' },
  { keys: ['沙漏', '钟', '表'], emoji: '⏳' },
  { keys: ['信', '信封'], emoji: '✉️' },
  { keys: ['笛', '琴', '乐器'], emoji: '🎵' },
  { keys: ['玩偶', '人偶'], emoji: '🧸' },
  { keys: ['贝壳'], emoji: '🐚' },
  { keys: ['骨头', '骨'], emoji: '🦴' },
  { keys: ['星'], emoji: '✨' }
]

/** 按物品名匹配图标，匹配不到时返回 ✦ */
export function emojiForItem(name) {
  const text = String(name || '')
  for (const item of ITEM_EMOJIS) {
    if (item.keys.some((key) => text.includes(key))) {
      return item.emoji
    }
  }
  return '✦'
}
