export const GAME_PACKAGE_MAX_SIZE = 3 * 1024 ** 3
export function validateGamePackageFile(platform, file) {
  const extensions = { android: /\.apk$/i, pc: /\.(exe|zip)$/i }
  if (!file || !file.size || file.size > GAME_PACKAGE_MAX_SIZE || !extensions[platform]?.test(file.name)) {
    return '请选择对应平台且不超过3 GiB的安装包'
  }
  return ''
}
export function formatPackageSize(bytes) {
  if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(2)} GiB`
  return `${(bytes / 1024 ** 2).toFixed(1)} MiB`
}
export function packageDownloadUrl(platform) {
  return ['android', 'pc'].includes(platform) ? `/api/portal/game-packages/${platform}/download` : ''
}
