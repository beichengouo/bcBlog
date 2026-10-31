// Usage: node scripts/import-game-codex.cjs <PixelRogue directory> <ffmpeg executable>
// Read-only game source export. Never writes to the game project.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto')
const { execFileSync } = require('node:child_process')
const root = path.resolve(process.argv[2]), ffmpeg = process.argv[3] || 'ffmpeg'
const front = path.resolve(__dirname, '..'), output = path.join(front, 'public/games/pixelrogue/codex')
fs.mkdirSync(output, { recursive: true })
function dictionary(file, key) {
  const text = fs.readFileSync(path.join(root, file), 'utf8')
  const start = text.indexOf('{', text.indexOf('const ' + key)), stack = []
  let quoted = false, escaped = false
  for (let i = start; i < text.length; i++) {
    const c = text[i]
    if (quoted) { if (escaped) escaped = false; else if (c === '\\') escaped = true; else if (c === '"') quoted = false; continue }
    if (c === '"') { quoted = true; continue }
    if (c === '{') stack.push(c)
    if (c === '}' && !--stack.length) return JSON.parse(text.slice(start, i + 1).replace(/&"/g, '"').replace(/,\s*}/g, '}'))
  }
  throw new Error('Missing dictionary ' + key)
}
const cached = new Map()
function asset(relative, maxWidth = 256) {
  if (!relative) return ''
  let source = path.resolve(root, relative.replace(/^tex:/, '').replace(/^res:\/\//, ''))
  if (!source.startsWith(root + path.sep) || !fs.existsSync(source)) throw new Error('Missing asset: ' + relative)
  const key = source + maxWidth
  if (cached.has(key)) return cached.get(key)
  let crop = ''
  const original = fs.readFileSync(source)
  if (source.endsWith('.tres')) {
    const text = original.toString(), texture = text.match(/path="(res:\/\/[^\"]+)"/), region = text.match(/region = Rect2\(([^)]+)\)/)
    if (!texture || !region) throw new Error('Unsupported atlas ' + relative)
    const [x,y,w,h] = region[1].split(',').map(Number)
    crop = `crop=${w}:${h}:${x}:${y},`
    source = path.resolve(root, texture[1].replace('res://', ''))
    if (!source.startsWith(root + path.sep)) throw new Error('Invalid atlas source')
  }
  const hash = crypto.createHash('sha256').update(original).update(fs.readFileSync(source)).update(String(maxWidth)).digest('hex').slice(0, 12)
  const name = path.basename(source, path.extname(source)) + '-' + hash + '.webp'
  const destination = path.join(output, name)
  if (!fs.existsSync(destination)) execFileSync(ffmpeg, ['-hide_banner', '-loglevel', 'error', '-i', source, '-vf', `${crop}scale=w='min(${maxWidth},iw)':h=-1`, '-frames:v', '1', '-c:v', 'libwebp', '-quality', '85', '-y', destination])
  const url = '/games/pixelrogue/codex/' + name
  cached.set(key, url)
  return url
}
const sets = dictionary('src/data/legendary_sets.gd', 'DATA')
const refreshed = dictionary('src/data/item_db.gd', 'REFRESHED_ICON_IDS')
const legendaryIds = Object.values(sets).flatMap(set => [set.weapon, set.armor, set.accessory])
const catalogue = JSON.parse(fs.readFileSync(path.join(root, 'data/items.json'), 'utf8'))
const items = catalogue.items.filter(item => ['weapon', 'armor', 'accessory', 'buff'].includes(item.kind)).map(item => {
  let icon = item.icon
  if (legendaryIds.includes(item.id)) icon = `res://assets/legendary_sets_v21/icons/${item.id}.png`
  else if (refreshed[item.id]) icon = `res://assets/items_v12/icons/${item.id}.png`
  if (typeof icon !== 'string' || !icon.includes('res://')) throw new Error('Unsupported icon: ' + item.id)
  return { id: item.id, name: item.name, kind: item.kind, category: item.kind === 'buff' ? 'relic' : 'equipment', rarity: item.rarity, description: item.desc || '', flavor: item.flavor || '', image: asset(icon) }
})
const cards = Object.entries(dictionary('src/data/fate_catalog.gd', 'CARDS')).map(([id, card]) => {
  const folder = ['fool', 'world', 'priestess', 'magician', 'moon'].includes(id) ? 'fate_v22' : 'fate_v01'
  return { id, ...card, category: 'fate', image: asset(`assets/${folder}/cards/${id}.png`, 400), reverseImage: asset(`assets/${folder}/cards/${id}_reverse.png`, 400) }
})
const costumes = Object.fromEntries(Object.entries(sets).map(([id, set]) => [id, { ...set, image: asset(`assets/legendary_sets_v21/${id}/portrait_set.png`, 800) }]))
const sceneRoot = 'assets/room_illustrated/city_star_canal_v01/'
const scene = JSON.parse(fs.readFileSync(path.join(root, sceneRoot, 'scene.json'), 'utf8'))
const layers = scene.planes.map(plane => ({ image: asset(sceneRoot + plane.path, 900), layer: plane.layer, rect: plane.rect_px }))
for (const gate of Object.values(scene.gates || {})) layers.push({ image: asset(sceneRoot + gate.path, 900), layer: 'gates', rect: gate.rect_px })
fs.writeFileSync(path.join(front, 'src/config/gameCodex.json'), JSON.stringify({ source: 'PixelRogue 游戏目录资料快照', updatedAt: new Date().toISOString().slice(0, 10), items, cards, costumes, cityScene: { width: scene.canvas[0], height: scene.canvas[1], layers } }, null, 2) + '\n')
console.log(JSON.stringify({ items: items.length, cards: cards.length, costumes: Object.keys(costumes).length, layers: layers.length, images: cached.size }))
